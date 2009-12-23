/*
 *
 *
 * Copyright (C) 2009 Nortel, certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxprovision.auto;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.Double;
import java.lang.Math;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.jetty.servlet.ServletHandler;

import org.sipfoundry.commons.util.ShortHash;
import org.sipfoundry.sipxprovision.auto.Configuration;

/**
 * A Jetty servlet that auto-provisions phones based on their HTTP requests.
 *
 * @author Paul Mossman
 */
@SuppressWarnings("serial")
public class Servlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger("Servlet");

    protected static Configuration m_config = null;

    private static final String MAC_RE_STR = "[0-9a-fA-F]{12}";

    public static final String ID_PREFIX = "ID: ";

    /**
     * Returns a simple unique-ish ID string (hash) generated from the specified seed.
     *
     * @see  org.sipfoundry.commons.util.ShortHash
     */
    protected static String getUniqueId(String seed_string) {

        return ShortHash.get(seed_string);
    }

    private static final String POLYCOM_PATH_PREFIX = "/";

    private static final String POLYCOM_PATH_FORMAT_RE_STR =
        "^" + POLYCOM_PATH_PREFIX + MAC_RE_STR + "-%s$";

    private static final Pattern POLYCOM_SIP_PATH_RE = Pattern.compile(
            String.format(POLYCOM_PATH_FORMAT_RE_STR, "sipx-sip.cfg"));

    private static final Pattern POLYCOM_PHONE1_PATH_RE = Pattern.compile(
            String.format(POLYCOM_PATH_FORMAT_RE_STR, "sipx-phone.cfg"));

    private static final Pattern POLYCOM_DEVICE_PATH_RE = Pattern.compile(
            String.format(POLYCOM_PATH_FORMAT_RE_STR, "sipx-device.cfg"));

    private static final Pattern POLYCOM_OVERRIDES_PATH_RE = Pattern.compile(
            String.format(POLYCOM_PATH_FORMAT_RE_STR, "phone.cfg"));

    private static final Pattern POLYCOM_CONTACTS_PATH_RE = Pattern.compile(
            String.format(POLYCOM_PATH_FORMAT_RE_STR, "directory.xml"));

    private static final Pattern POLYCOM_000000000000_CONTACTS_PATH_RE = Pattern.compile(
            "^" + POLYCOM_PATH_PREFIX + "000000000000-directory.xml$");

    private static final String POLYCOM_UA_DELIMITER = "UA/";


    private static final String NORTEL_IP_12X0_PATH_PREFIX = "/Nortel/config/SIP";

    private static final Pattern NORTEL_IP_12X0_PATH_RE =
        Pattern.compile("^" + NORTEL_IP_12X0_PATH_PREFIX + MAC_RE_STR + ".xml$");

    private static final String NORTEL12X0_UA_PREFIX_STR = "Nortel IP Phone 12";

    private static final Pattern NORTEL12X0_UA_RE = Pattern.compile(
            String.format("^%s[1-3]0 \\(SIP12x0[0-9\\.]*\\)$", NORTEL12X0_UA_PREFIX_STR));


    private static final Pattern EXACT_MAC_RE = Pattern.compile("^" + MAC_RE_STR + "$");

    protected static boolean isMacAddress(String mac) {
        return EXACT_MAC_RE.matcher(mac).matches();
    }

    /**
     * Extracts the MAC from the specified path, starting immediately after the specified prefix.
     *
     * @return the MAC, or null on failure.
     */
    protected static String extractMac(String path, String prefix) {
        try {
            String mac = path.substring(prefix.length(), prefix.length()+12);
            if (isMacAddress(mac)) {
                return mac.toLowerCase();
            }
        }
        catch (Exception e) {}

        return null;
    }

    /**
     * Starts the Jetty servlet that handles auto-provision requests
     */
    public static void start() {
        try {
            // The configuration.
            m_config = new Configuration();
            m_config.configureLog4j();

            LOG.info("START.");
            LOG.debug(String.format("Unique ID - length: %d  set size: %d  combinations: %d.",
                    ShortHash.ID_LENGTH, ShortHash.ID_CHARS.length,
                    new Double(Math.pow(ShortHash.ID_CHARS.length, ShortHash.ID_LENGTH)).intValue()));

            // Dump the configuration into the log.
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            m_config.dumpConfiguration(new PrintWriter(stream));
            String[] lines = stream.toString().split("\\n");
            for (String line : lines){
                LOG.info(line);
            }

            // This is what causes non-provisioned Polycoms to the HTTP request to the servlet.
            initializeStaticConfig(m_config);

            // Start up jetty.
            HttpServer server = new HttpServer();

            // Bind the port on all interfaces.
            server.addListener(":" + m_config.getServletPort());

            HttpContext httpContext = new HttpContext();
            httpContext.setContextPath("/");

            // Setup the servlet to call the class when the URL is fetched.
            ServletHandler servletHandler = new ServletHandler();
            servletHandler.addServlet(Servlet.class.getCanonicalName(), m_config.getServletUriPath()
                    + "/*", Servlet.class.getName());
            httpContext.addHandler(servletHandler);
            server.addContext(httpContext);

            // Start it up.
            LOG.info(String.format("Starting %s servlet on *:%d%s", Servlet.class.getCanonicalName(),
                    m_config.getServletPort(), m_config.getServletUriPath()));
            server.start();
        } catch (Exception e) {
            LOG.error("Failed to start the servlet:", e);
        }
    }

    public static class PolycomVelocityCfg {

        Configuration m_config;

        PolycomVelocityCfg(Configuration config) {
            m_config = config;
        }

        public String getSipBinaryFilename() {
            return "sip.ld";
        }

        public String getRootUrlPath() {
            return String.format("http://%s:%d%s/", m_config.getHostname(),
                    m_config.getServletPort(), m_config.getServletUriPath());
        }
    }

    /**
     * This content is static only while the servlet is running.  A configuration change of the
     * servlet port will change the content, but this will be done during the servlet re-start.
     * @throws Exception
     * @throws ParseErrorException
     * @throws ResourceNotFoundException
     */
    private static void initializeStaticConfig(Configuration config) {

        // Generate the Polycom 000000000000.cfg, which will cause un-provisioned phones to send
        // HTTP requests to this servlet.
        File polycom_src_dir = new File(System.getProperty("conf.dir") + "/polycom");
        try {

            Properties p = new Properties();
            p.setProperty("resource.loader", "file");
            p.setProperty("class.resource.loader.class",
                    "org.apache.velocity.runtime.resource.loader.FileResourceLoader" );
            p.setProperty("file.resource.loader.path", polycom_src_dir.getAbsolutePath());
            Velocity.init(p);

            Template template = Velocity.getTemplate("mac-address.cfg.vm");

            VelocityContext context = new VelocityContext();
            context.put("cfg", new PolycomVelocityCfg(config));

            Writer writer = new FileWriter(new File(config.getTftpPath(), "/000000000000.cfg"));
            template.merge(context, writer);
            writer.flush();

            LOG.info(String.format("Generated Polycom 000000000000.cfg from %s.", polycom_src_dir.getAbsolutePath()));

        } catch (ResourceNotFoundException e) {
            LOG.error("Failed to generate Polycom 000000000000.cfg: ", e);
        } catch (ParseErrorException e) {
            LOG.error("Failed to generate Polycom 000000000000.cfg: ", e);
        } catch (Exception e) {
            LOG.error("Velocity initialization error:", e);
        }

        // Copy the Polycom static files.  (See: http://list.sipfoundry.org/archive/sipx-dev/msg20157.html)
        try {

            FilenameFilter cfg_filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("polycom_") && name.endsWith(".cfg");
                }
            };

            File[] files = polycom_src_dir.listFiles(cfg_filter);
            if (null == files) {
               LOG.error(String.format("No Polycom static files found at %s.", polycom_src_dir.getAbsolutePath()));
            }
            else {
                int copy_count = 0;
                for (File file : files) {

                    File dest_file = new File(config.getTftpPath(), file.getName());
                    if (!dest_file.exists()) {

                        OutputStream output = new BufferedOutputStream(new FileOutputStream(dest_file));
                        FileInputStream input = null;
                        try {
                            input = new FileInputStream(file);
                            IOUtils.copy(input, output);
                            copy_count++;
                        }
                        catch (IOException e) {
                            LOG.error(String.format("Failed to copy Polycom static file %s.", file.getName()), e);
                        }
                        finally {
                           IOUtils.closeQuietly(input);
                           IOUtils.closeQuietly(output);
                        }
                    }
                }

                LOG.info(String.format("Copied %d (of %d) Polycom static files from %s.", copy_count, files.length,
                        polycom_src_dir.getAbsolutePath()));
            }
        }
        catch(Exception e ) {
            LOG.error("Failed to copy Polycom static files from " + polycom_src_dir.getAbsolutePath() + ":", e);
        }

        // Generate the Nortel IP 12x0 SIPdefault.xml, which will cause un-provisioned phones to send
        // an HTTP request to this servlet.
        try {
            File dir = new File(config.getTftpPath() + "/Nortel/config");
            if (!dir.exists() && !dir.mkdirs()) {
                LOG.error("Failed to create directory: " + dir);
            }
            else {

                File file = new File(dir + "/SIPdefault.xml");

                FileOutputStream fos = new java.io.FileOutputStream(file);
                java.io.PrintStream ps = new java.io.PrintStream(fos);
                ps.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
                ps.println(String.format("<redirect>http://%s:%d%s</redirect>", m_config.getHostname(),
                        m_config.getServletPort(), m_config.getServletUriPath()));
                fos.close();

                LOG.info("Generated Nortel IP 12x0 SIPdefault.xml.");
            }
        }
        catch(Exception e ) {
            LOG.error("Failed to generate Nortel IP 12x0 SIPdefault.xml: ", e);
        }
    }

    private void logAndOut(String msg, PrintWriter out) {
        LOG.debug(msg);
        out.println(msg);
    }

    protected void writeDebuggingResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Do some debugging.
        LOG.debug("Debugging: " + request.getPathInfo());
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();

        logAndOut("getPathInfo(): '" + request.getPathInfo() + "'", out);
        logAndOut("getQueryString(): '" + request.getQueryString() + "'", out);
        logAndOut("getRequestURI(): '" + request.getRequestURI() + "'", out);
        logAndOut("getRemoteUser(): '" + request.getRemoteUser() + "'", out);
        logAndOut("getServletPath(): '" + request.getServletPath() + "'", out);
        logAndOut("getContextPath(): '" + request.getContextPath() + "'", out);
        logAndOut("getLocalAddr(): '" + request.getLocalAddr() + "'", out);
        logAndOut("getServerName(): '" + request.getServerName() + "'", out);
        logAndOut("getServerPort(): '" + request.getServerPort() + "'", out);
        logAndOut("getHeader(\"User-Agent\"): '" + request.getHeader("User-Agent") + "'", out);
        logAndOut("", out);

        logAndOut("request: " + request, out);
        m_config.dumpConfiguration(out);
    }

    /**
     * Creates a (single-use) HTTPS connection to the sipXconfig REST Phones resource
     * server.
     *
     *  @return the HTTPS connection, or null upon failure.
     */
    protected HttpsURLConnection createRestConnection(String method, String string_url) {
        LOG.debug("Creating connection: " + method + " " + string_url);

        HttpsURLConnection connection = null;
        try {
            URL url = new URL(string_url);
            connection = (HttpsURLConnection) url.openConnection();

            // TODO --> org.apache.http.auth.UsernamePasswordCredentials ?????
            connection.setRequestProperty("Authorization", "Basic " +
                    m_config.getBase64ConfigurationRestCredentials());
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod(method);
            LOG.debug("Connection is good.");

        } catch (Exception e) {
            LOG.error("Failed to create HttpsURLConnection:", e);
            connection =  null;
        }

        return connection;
    }

    /**
     * Create a Description text for the specified phone.
     *
     *  @see provisionPhones
     *  @return the string description.
     */
    protected static String getPhoneDescription(DetectedPhone phone, Date date) {
        return String.format(
                "Auto-provisioned\n  %s%s\n  Model: %s\n  Version: %s\n",
                ID_PREFIX, getUniqueId(phone.mac), phone.model.full_label, phone.version);
    }

    protected boolean doProvisionPhone(DetectedPhone phone) {

        if (null == phone) {
            return false;
        }

        boolean success = false;
        try {
            LOG.info("doProvisionPhone - " + phone);

            // Write the REST representation of the phone(s).
            HttpsURLConnection connection = createRestConnection("POST", m_config.getConfigurationUri() + "/rest/phone");
            DataOutputStream dstream = new java.io.DataOutputStream(connection.getOutputStream());
            dstream.writeBytes("<phones>");
            dstream.writeBytes(String.format("<phone><serialNumber>%s</serialNumber>" +
               "<model>%s</model><description>%s</description></phone>",
               phone.mac.toLowerCase(), phone.model.sipxconfig_id, getPhoneDescription(phone, new Date())));
            dstream.writeBytes("</phones>");

            // Do the HTTPS POST.
            dstream.close();

            // Record the result.  (Only transport, internal, etc. errors will be reported.
            // Duplicate and/or invalid MACs for example will result in 200 OK.)
            if (HttpsURLConnection.HTTP_OK == connection.getResponseCode()) {
                success = true;
                LOG.info("REST HTTPS POST success.");
            } else {
                LOG.error("REST HTTPS POST failed:" + connection.getResponseCode() + " - " +
                        connection.getResponseMessage() );
            }

        } catch (Exception e) {
            LOG.error("REST HTTPS POST failed:", e);
        }

        return success;
    }

    protected boolean isDebugTestUserAgent(String useragent) {
        return (useragent.contains("Gecko") || useragent.contains("curl")) && m_config.isDebugOn();
    }

    protected boolean isPolycomConfigurationFilePath(String path) {
        return POLYCOM_SIP_PATH_RE.matcher(path).matches() ||
            POLYCOM_PHONE1_PATH_RE.matcher(path).matches() ||
            POLYCOM_DEVICE_PATH_RE.matcher(path).matches() ||
            POLYCOM_OVERRIDES_PATH_RE.matcher(path).matches() ||
            POLYCOM_CONTACTS_PATH_RE.matcher(path).matches() ||
            POLYCOM_000000000000_CONTACTS_PATH_RE.matcher(path).matches();
    }

    protected boolean isNortelIp12x0ConfigurationFilePath(String path) {
        return NORTEL_IP_12X0_PATH_RE.matcher(path).matches();
    }

    // TODO: Test case (x2 phone types) when MAC is too short (should not invoke the do___Get...)
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String path = request.getPathInfo();
        String useragent = request.getHeader("User-Agent");
        LOG.info("GET " + path + "  User-Agent: " + useragent);

        // Examine the User-Agent.
        if (null == useragent || useragent.isEmpty()) {

            // Can't do anything without this.
            writeUiForwardResponse(request, response);
            return;
        }

        // For debugging.
        if (isDebugTestUserAgent(useragent)) {
            if (isPolycomConfigurationFilePath(path)) {
                useragent = "FileTransport PolycomSoundStationIP-SSIP_6000-UA/3.2.0.0157";
            }
            else if (isNortelIp12x0ConfigurationFilePath(path)) {
                useragent = "Nortel IP Phone 1210 (SIP12x0.45.02.05.00)";
            }
        }

        // Is this a path that triggers provisioning of a phone with sipXconfig?
        DetectedPhone phone = null;
        if (POLYCOM_SIP_PATH_RE.matcher(path).matches()) {

            // Polycom SoundPoint IP / SoundStation IP / VVX
            phone = new DetectedPhone();

            // MAC, Model, & Version
            phone.mac = extractMac(path, POLYCOM_PATH_PREFIX);
            if (null == phone.mac || !extractPolycomModelAndVersion(phone, useragent)) {
                writeUiForwardResponse(request, response);
                return;
            }
        }
        else if (NORTEL_IP_12X0_PATH_RE.matcher(path).matches()) {

            // Nortel IP 1210/1220/1230
            phone = new DetectedPhone();

            // MAC, Model, & Version
            phone.mac = extractMac(path, NORTEL_IP_12X0_PATH_PREFIX);
            if (null == phone.mac || !extractNortelIp12X0ModelAndVersion(phone, useragent)) {
                writeUiForwardResponse(request, response);
                return;
            }
        }
        else if (path.startsWith("/debug") && m_config.isDebugOn()) {
            // Debugging.
            writeDebuggingResponse(request, response);
            return;
        }
/*  TODO: Test case to catch this problem....      else {
            writeUiForwardResponse(request, response);
            return;
        }
  */
        // Provision the phone with sipXconfig.  (If it wasn't a provision trigger path, then phone will
        // be null, which fails cleanly.)
        doProvisionPhone(phone);

        // Return the appropriate actual profile content.
        response.setContentType("application/text");
        if (isPolycomConfigurationFilePath(path)) {

            if (POLYCOM_000000000000_CONTACTS_PATH_RE.matcher(path).matches()) {

                // This file won't be found, so save the REST request overhead.
                response.sendError(HttpURLConnection.HTTP_NOT_FOUND);
            }
            else if (POLYCOM_OVERRIDES_PATH_RE.matcher(path).matches()) {

                // sipXconfig doesn't currently generate this Polycom config file.  (See XX-5450)
                PrintWriter out = response.getWriter();
                out.println("<?xml version=\"1.0\" standalone=\"yes\"?>");
                out.println("<PHONE_CONFIG><OVERRIDES/></PHONE_CONFIG>");
            }
            else {
                writeProfileConfigurationResponse(path, extractMac(path, POLYCOM_PATH_PREFIX), response);
            }
        }
        else if (isNortelIp12x0ConfigurationFilePath(path)) {

            writeProfileConfigurationResponse(path, extractMac(path, NORTEL_IP_12X0_PATH_PREFIX), response);
        }
        else {

            // Unknown configuration file path.  (Wouldn't know how to extract a MAC from the path.)
            writeUiForwardResponse(request, response);
            return;
        }
    }

    protected boolean writeProfileConfigurationResponse(String path, String mac, HttpServletResponse response) {

        boolean success = false;
        try {
            LOG.info("writeConfigurationResponse - " + path);

            String encoded_path = new String() + path.charAt(0) + URLEncoder.encode(path.substring(1), "UTF-8");

            // Construct the REST request.
            String uri = String.format("%s/rest/phone/%s/profile%s", m_config.getConfigurationUri(),
                    mac, encoded_path);
            HttpsURLConnection connection = createRestConnection("GET", uri);

            // Do the HTTPS GET, and write the content.
            connection.connect();
            IOUtils.copy(connection.getInputStream(), response.getOutputStream());

            // Record the result.  (Only transport, internal, etc. errors will be reported.
            // Duplicate and/or invalid MACs for example will result in 200 OK.)
            if (HttpsURLConnection.HTTP_OK == connection.getResponseCode()) {
                success = true;
                LOG.info("REST HTTPS GET success.");
            } else {
                LOG.error("REST HTTPS GET failed:" + connection.getResponseCode() + " - " +
                        connection.getResponseMessage() );
            }

        } catch (Exception e) {
            LOG.error("REST HTTPS GET failed:", e);
        }

        return success;
    }

    protected static boolean extractPolycomModelAndVersion(DetectedPhone phone, String useragent) {

        if (null == useragent ||
            null == phone
            // TODO regexp match, makes below parsing easier --> ! POLYCOM_UA_RE.matcher(useragent).matches()
            ) {
            return false;
        }

        // Model
        int i1 = useragent.indexOf("-");
        int i2 = useragent.indexOf("-", i1+1);
        if (-1 == i1 || -1 == i2) {
            return false;
        }
        String model_id = useragent.substring(i1+1, i2);
        phone.model = lookupPhoneModel(model_id);
        if (null == phone.model) {
            return false;
        }

        // Version (optional)
        i1 = useragent.indexOf(POLYCOM_UA_DELIMITER);
        if (-1 != i1) {
            i2 = useragent.indexOf(" ", i1 + POLYCOM_UA_DELIMITER.length());
            if (-1 == i2) {
                i2 = useragent.length();
            }
            phone.version = useragent.substring(i1 + POLYCOM_UA_DELIMITER.length(), i2);
        }

        return true;
    }

    protected static boolean extractNortelIp12X0ModelAndVersion(DetectedPhone phone, String useragent) {

        if (null == useragent ||
            null == phone ||
            ! NORTEL12X0_UA_RE.matcher(useragent).matches() ) {
            return false;
        }

        // Model
        String model_string = String.format("12%c0", useragent.charAt(NORTEL12X0_UA_PREFIX_STR.length()));
        phone.model = lookupPhoneModel(model_string);
        if (null == phone.model) {
            return false;
        }

        // Version
        int i1 = useragent.indexOf("(");
        phone.version = useragent.substring(i1 + 1, useragent.indexOf(")", i1 + 1));

        return true;
    }

    // TODO - test cases for various problems that could occur with
    private static void writeUiForwardResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {

        LOG.debug("Un-recognized user-agent or path.  Redirecting to sipXconfig.");

        // Try to redirect with the servername used in the request.  Helpful if the client can't
        // resolve the FQDN that we're using.
        String redirect_location = m_config.getConfigurationUri();
        try {
            URI config_uri = new URI(redirect_location);
            URI redirect_uri = new URI(config_uri.getScheme(), null, request.getServerName(),
                    config_uri.getPort(), config_uri.getPath(), null, null);

            redirect_location = redirect_uri.toString();
        }
        catch (URISyntaxException e) {
            LOG.debug("Failed to customize re-direct URI based on request parameters: ", e);
        }

        // The JavaScript to redirect.
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html><head><script language=\"JavaScript\">");
        out.println("<!-- Hide script from old browsers");
        out.println("location = '" + redirect_location + "'");
        out.println("// End script hiding from old browsers -->");
        out.println("</script></head><body></body></html>");
    }

    public static class PhoneModel {
        PhoneModel(String id, String label) {
            sipxconfig_id = id;
            full_label = label;
        }
        public final String sipxconfig_id;
        public final String full_label;
    }

    public static class DetectedPhone {
        public PhoneModel model = new PhoneModel("unknown", "unknown");
        public String version = new String("unknown");
        public String mac = null;
        public String toString() {
            return "id: " + getUniqueId(mac) + "  mac: " + mac + "  version: " + version +
                "  model: " + model.full_label;
        }
    }

    static public PhoneModel lookupPhoneModel(String index) {
        return PHONE_MODEL_MAP.get(index);
    }

    private static final HashMap<String, PhoneModel> PHONE_MODEL_MAP;
    static {
        PHONE_MODEL_MAP = new HashMap<String, PhoneModel>();

        // Polycom SoundPoing IP family, see:
        //  - http://sipx-wiki.calivia.com/index.php/Polycom_SoundPoint_IP_family_table
        //  - plugins/polycom/src/org/sipfoundry/sipxconfig/phone/polycom/polycom-models.beans.xml
        PHONE_MODEL_MAP.put("SPIP_300", new PhoneModel("polycom300", "SoundPoint IP 300"));
        PHONE_MODEL_MAP.put("SPIP_301", new PhoneModel("polycom300", "SoundPoint IP 301"));

        PHONE_MODEL_MAP.put("SPIP_320", new PhoneModel("polycom330", "SoundPoint IP 320"));
        PHONE_MODEL_MAP.put("SPIP_321", new PhoneModel("polycom330", "SoundPoint IP 321"));
        PHONE_MODEL_MAP.put("SPIP_330", new PhoneModel("polycom330", "SoundPoint IP 330"));
        PHONE_MODEL_MAP.put("SPIP_331", new PhoneModel("polycom330", "SoundPoint IP 331"));
        PHONE_MODEL_MAP.put("SPIP_335", new PhoneModel("polycom335", "SoundPoint IP 335"));

        PHONE_MODEL_MAP.put("SPIP_430", new PhoneModel("polycom430", "SoundPoint IP 430"));
        PHONE_MODEL_MAP.put("SPIP_450", new PhoneModel("polycom450", "SoundPoint IP 450"));

        PHONE_MODEL_MAP.put("SPIP_500", new PhoneModel("polycom500", "SoundPoint IP 500"));
        PHONE_MODEL_MAP.put("SPIP_501", new PhoneModel("polycom500", "SoundPoint IP 501"));

        PHONE_MODEL_MAP.put("SPIP_550", new PhoneModel("polycom550", "SoundPoint IP 550"));
        PHONE_MODEL_MAP.put("SPIP_560", new PhoneModel("polycom550", "SoundPoint IP 560"));

        PHONE_MODEL_MAP.put("SPIP_600", new PhoneModel("polycom600", "SoundPoint IP 600"));
        PHONE_MODEL_MAP.put("SPIP_601", new PhoneModel("polycom600", "SoundPoint IP 601"));

        PHONE_MODEL_MAP.put("SPIP_650", new PhoneModel("polycom650", "SoundPoint IP 650"));
        PHONE_MODEL_MAP.put("SPIP_670", new PhoneModel("polycom650", "SoundPoint IP 670"));

        PHONE_MODEL_MAP.put("SSIP_4000", new PhoneModel("polycom4000", "SoundStation IP 4000"));

        PHONE_MODEL_MAP.put("SSIP_6000", new PhoneModel("polycom6000", "SoundStation IP 6000"));

        PHONE_MODEL_MAP.put("SSIP_7000", new PhoneModel("polycom7000", "SoundStation IP 7000"));

        PHONE_MODEL_MAP.put("VVX_1500", new PhoneModel("polycomVVX1500", "Polycom VVX 1500"));

        // Nortel IP 12x0, see:
        //  - http://sipx-wiki.calivia.com/index.php/Polycom_SoundPoint_IP_family_table
        //  - plugins/nortel12x0/src/org/sipfoundry/sipxconfig/phone/nortel12x0/nortel12x0-models.beans.xml
        PHONE_MODEL_MAP.put("1210", new PhoneModel("nortel-1210", "Nortel IP 1210"));
        PHONE_MODEL_MAP.put("1220", new PhoneModel("nortel-1220", "Nortel IP 1220"));
        PHONE_MODEL_MAP.put("1230", new PhoneModel("nortel12x0PhoneStandard", "Nortel IP 1230"));
    }

    public static void main(String[] args) throws Exception {

        // Send log4j output to the console.
        org.apache.log4j.BasicConfigurator.configure();

        System.out.println("MAIN - " + Servlet.class.getCanonicalName().toString());

        Velocity.init();

        System.out.println("Velocity initialized...");
    }
}
