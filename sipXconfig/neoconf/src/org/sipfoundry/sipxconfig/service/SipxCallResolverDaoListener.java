/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.service;

import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.common.event.DaoEventListener;


public class SipxCallResolverDaoListener implements DaoEventListener {

    private SipxServiceManager m_sipxServiceManager;
    private ServiceConfigurator m_serviceConfigurator;

    public void onDelete(Object entity) {
        if (entity instanceof Location) {
          // A location has been deleted. We need to regenerate the callresolver-config on the master.
            Location location = (Location) entity;
            SipxService callresolverService = m_sipxServiceManager.getServiceByBeanId(SipxCallResolverService.BEAN_ID);
            m_serviceConfigurator.replicateServiceConfig(callresolverService);

        }
    }

    public void onSave(Object entity) {
        if (entity instanceof Location) {
            // A new location has been added.  We need to regenerate the callresolver-config on the master.
            Location location = (Location) entity;
            SipxService callresolverService = m_sipxServiceManager.getServiceByBeanId(SipxCallResolverService.BEAN_ID);
            m_serviceConfigurator.replicateServiceConfig(callresolverService);
        }
    }

    public void setSipxServiceManager(SipxServiceManager sipxServiceManager) {
        m_sipxServiceManager = sipxServiceManager;
    }

    public void setServiceConfigurator(ServiceConfigurator serviceConfigurator) {
        m_serviceConfigurator = serviceConfigurator;
    }
}
