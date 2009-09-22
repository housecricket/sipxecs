//
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
//
// $$
////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES
#include <stdio.h>
#include <assert.h>
#if defined(_WIN32)
# include <io.h>
# define STDIN_FILENO 0 /* can't find where windows defines this */
#else
# include <unistd.h>
#endif

// APPLICATION INCLUDES
#include <os/OsDefs.h>
#include <siptest/CommandProcessor.h>
#include <net/SipUserAgent.h>
#include <net/SipLineMgr.h>
#include <net/SipRefreshMgr.h>
#include <siptest/SipSendCommand.h>
#include <siptest/SipLSendCommand.h>
#include <siptest/ExitCommand.h>
#include <siptest/HttpGetCommand.h>
#include <siptest/HelpCommand.h>
#include <siptest/HistoryCommand.h>
#include <siptest/CommandMsgProcessor.h>
#include <siptest/RespondCommand.h>
#include <siptest/RespondTemplate.h>
#include <siptest/SipLogCommand.h>
#include <siptest/AuthCommand.h>

// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STATIC VARIABLE INITIALIZATIONS
// FUNCTIONS

class SleepCommand : public Command
{
public:
   SleepCommand(){};
   //:Default constructor

   virtual
   ~SleepCommand(){};
   //:Destructor

   virtual int execute(int argc, char* argv[])
      {
         int commandStatus = CommandProcessor::COMMAND_FAILED;
         if(argc != 2)
         {
            UtlString usage;
            getUsage(argv[0], &usage);
            printf("%s", usage.data());
            commandStatus = CommandProcessor::COMMAND_BAD_SYNTAX;
         }
         else
         {
            int msecs = (atoi(argv[1])) * 1000;

            if(msecs > 0)
            {
               commandStatus = CommandProcessor::COMMAND_SUCCESS;
               OsTask::delay(msecs);
            }
         }
         return(commandStatus);
      }

   virtual void getUsage(const char* commandName, UtlString* usage) const
      {
         Command::getUsage(commandName, usage);
         usage->append(" <seconds>\n");
      }

private:

   SleepCommand(const ExitCommand& rExitCommand);
   //:Copy constructor
   SleepCommand& operator=(const ExitCommand& rhs);
   //:Assignment operator
};


class ResendResponse : public Command
{
public:
   ResendResponse(CommandMsgProcessor* proc){mMessageProcessor = proc;};
   //:Default constructor

   virtual
   ~ResendResponse(){};
   //:Destructor

   virtual int execute(int argc, char* argv[])
      {
         int commandStatus = CommandProcessor::COMMAND_FAILED;
         if(argc != 1)
         {
            UtlString usage;
            getUsage(argv[0], &usage);
            printf("%s", usage.data());
            commandStatus = CommandProcessor::COMMAND_BAD_SYNTAX;
         }
         else
         {
            mMessageProcessor->resendLastResponse();
         }

         return(commandStatus);
      }

   virtual void getUsage(const char* commandName, UtlString* usage) const
      {
         Command::getUsage(commandName, usage);
         usage->append("\n\tResends the last response generated by the \nrt or response commands\n");
      }

private:
   CommandMsgProcessor* mMessageProcessor;

   ResendResponse(const ExitCommand& rExitCommand);
   //:Copy constructor
   ResendResponse& operator=(const ExitCommand& rhs);
   //:Assignment operator
};


int main(int argc, char* argv[])
{
   const char* configFileName = "siptest-config";
   int proxyTcpPort;
   int proxyUdpPort;
   int proxyTlsPort;
   OsConfigDb configDb;

   // siptest uses osPrintf for output, so we have to un-suppress it.
   enableConsoleOutput(TRUE);

   if(configDb.loadFromFile(configFileName) == OS_SUCCESS)
   {
      osPrintf("Found config file: %s\n", configFileName);
   }
   else
   {
      configDb.set("SIP_TEST_UDP_PORT", "3000");
      configDb.set("SIP_TEST_TCP_PORT", "3000");
      configDb.set("SIP_TEST_TLS_PORT", "3001");

      if(configDb.storeToFile(configFileName) == OS_SUCCESS)
      {
         osPrintf("Could not write config file: %s\n", configFileName);
      }
   }

   proxyUdpPort = configDb.getPort("SIP_TEST_UDP_PORT") ;
   proxyTcpPort = configDb.getPort("SIP_TEST_TCP_PORT") ;
   proxyTlsPort = configDb.getPort("SIP_TEST_TLS_PORT") ;

   UtlBoolean commandStatus = CommandProcessor::COMMAND_SUCCESS;
   char buffer[1024];
   char* commandLine;
   CommandProcessor processor;

   SipLineMgr*    lineMgr = new SipLineMgr();
   SipRefreshMgr* refreshMgr = new SipRefreshMgr();

   lineMgr->StartLineMgr();
   lineMgr->initializeRefreshMgr( refreshMgr );

   SipUserAgent*  sipUA = new SipUserAgent( proxyTcpPort
                                            ,proxyUdpPort
                                            ,proxyTlsPort
                                            ,NULL         // default publicAddress
                                            ,NULL         // default defaultUser
                                            ,NULL         // default defaultSipAddress
                                            ,NULL         // default sipProxyServers
                                            ,NULL         // default sipDirectoryServers
                                            ,NULL         // default sipRegistryServers
                                            ,NULL         // default authenticationScheme
                                            ,NULL         // default authenicateRealm
                                            ,NULL         // default authenticateDb
                                            ,NULL         // default authorizeUserIds
                                            ,NULL         // default authorizePasswords
                                            ,lineMgr
      );
   sipUA->allowMethod(SIP_REGISTER_METHOD);
   sipUA->allowMethod(SIP_SUBSCRIBE_METHOD);
   sipUA->allowMethod(SIP_NOTIFY_METHOD);

   sipUA->start();

   sipUA->startMessageLog();
   osPrintf( "SIP logging Started\n" );

   refreshMgr->init( sipUA );

   CommandMsgProcessor* msgProc = new CommandMsgProcessor(sipUA);
   msgProc->start();

   processor.registerCommand("help", new HelpCommand(&processor));
   processor.registerCommand("?", new HelpCommand(&processor));
   processor.registerCommand("history", new HistoryCommand(&processor));
   processor.registerCommand("send", new SipSendCommand(sipUA));
   processor.registerCommand("lsend", new SipLSendCommand());
   processor.registerCommand("get", new HttpGetCommand());
   processor.registerCommand("respond", new RespondCommand(msgProc));
   processor.registerCommand("rt", new RespondTemplate(msgProc));
   processor.registerCommand("rrespond", new ResendResponse(msgProc));
   processor.registerCommand("log", new SipLogCommand(*sipUA));
   processor.registerCommand("auth", new AuthCommand(lineMgr));
   processor.registerCommand("sleep", new SleepCommand());
   processor.registerCommand("quit", new ExitCommand());
   processor.registerCommand("exit", new ExitCommand());

   //Initialization
   UtlBoolean doPrompt = isatty(STDIN_FILENO);

   if ( doPrompt )
   {
      printf("Enter command or help/? for help\n");
      printf("SIPdriver: ");
   }

   for ( commandStatus = CommandProcessor::COMMAND_SUCCESS;
         (   commandStatus < CommandProcessor::COMMAND_FAILED_EXIT
             && commandStatus != CommandProcessor::COMMAND_SUCCESS_EXIT
             && (commandLine = fgets(buffer,1024,stdin))
            );
      )
   {
      //printf("GOT command line:\"%s\"\n", commandLine);
      commandStatus = processor.executeCommand(commandLine);
      //printf("command status: %d exit status: %d\n", commandStatus,
      //CommandProcessor::COMMAND_SUCCESS_EXIT);
      if ( doPrompt )
      {
         printf("SIPdriver: ");
      }
   }

   delete msgProc;
   delete sipUA;

   return CommandProcessor::COMMAND_SUCCESS_EXIT != commandStatus;
}
