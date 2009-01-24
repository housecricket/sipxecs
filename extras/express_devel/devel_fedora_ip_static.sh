#!/bin/bash
#
# Copyright (C) 2009 Nortel, certain elements licensed under a Contributor Agreement.  
# Contributors retain copyright to elements licensed under a Contributor Agreement.
# Licensed to the User under the LGPL license.
#
###################################################

# Changes a Fedora 10 system from DHCP to static using the specified IP Address.  It 
# also attempts to configure the hostname accordingly, and restarts the network 
# service to effect the changes.  (It is best run from the machine console, and not an
# ssh session.)

if [ "`whoami`" != root ]
then
  echo "You must be root in order to run this script."
  exit 1
fi

if [ $# -lt 1 -o $# -gt 1 ]
then
  echo "Usage: ${0} <IP Address>"
  exit 2
fi

FQDN=`dig -x $1 | grep -b1 "ANSWER SECTION" | tail -1 | cut -f3 | sed -e "s/.$//g"`
if [ "$FQDN" == "" ]
then
   FQDN=`hostname -f`
fi
HOSTNAME=`echo $FQDN | cut -d. -f1`
if [ "$HOSTNAME" == "" ]
then
  HOSTNAME=`hostname -s`
fi

GATEWAY=`/sbin/route -v -n | grep " UG " | ruby -e 'puts STDIN.readline.split[1]'`
if [ "$GATEWAY" == "" ]
then
  echo "Failed to determine gateway address."
  exit 3
fi

SUBNET=`/sbin/route -v | head -n3 | tail -n1 | ruby -e 'puts STDIN.readline.split[2]'`
if [ "$SUBNET" == "" ]
then
  echo "Failed to determine subnet mask."
  exit 4
fi

echo "   IP Address : $1"
echo "   Subnet Mask: $SUBNET"
echo "   FQDN       : $FQDN"
echo "   Hostname   : $HOSTNAME"
echo "   Gateway    : $GATEWAY"

FILE_ETH0=/etc/sysconfig/network-scripts/ifcfg-eth0
FILE_NETWORK=/etc/sysconfig/network
FILE_HOSTS=/etc/hosts
FILE_RESOLV_CONF=/etc/resolv.conf

# /etc/sysconfig/network-scripts/ifcfg-eth0
echo "DEVICE=eth0" > $FILE_ETH0
echo "ONBOOT=yes" >> $FILE_ETH0
echo "BOOTPROTO=static" >> $FILE_ETH0
echo "IPADDR=$1" >> $FILE_ETH0
echo "NETMASK=$SUBNET" >> $FILE_ETH0

# /etc/hosts
cat $FILE_HOSTS | head -4 > $FILE_HOSTS
echo "$1 $FQDN $HOSTNAME" >> $FILE_HOSTS

# /etc/sysconfig/network
sed -i -e "s/HOSTNAME/# HOSTNAME/g" $FILE_NETWORK
echo "HOSTNAME=$FQDN" >> $FILE_NETWORK
sed -i -e "s/GATEWAY/# GATEWAY/g" $FILE_NETWORK
echo "GATEWAY=$GATEWAY" >> $FILE_NETWORK

# hostname
hostname $FQDN

# Copy the existing /etc/resolv.conf, it will do nicely.
COPY_RESOLV_CONF=/tmp/COPY_resolv.conf
rm -rf $COPY_RESOLV_CONF
cp $FILE_RESOLV_CONF $COPY_RESOLV_CONF
sed -i -e "s/generated by \/sbin\/dhclient-script/Cloned.../g" $COPY_RESOLV_CONF
echo "Cloned..." >> $COPY_RESOLV_CONF

# The first restart clears the existing /etc/resolv.conf.
/etc/init.d/network restart

# Move the copy back, and restart again.
mv -f $COPY_RESOLV_CONF $FILE_RESOLV_CONF
/etc/init.d/network restart

# Show off the fruits of our labours.
ping -c 1 $FQDN 
ping -c 1 nortel.com
