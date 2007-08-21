/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.admin.forwarding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.sipfoundry.sipxconfig.admin.commserver.SipxReplicationContext;
import org.sipfoundry.sipxconfig.admin.commserver.imdb.DataSet;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.DSTChangeEvent;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.common.event.UserDeleteListener;
import org.sipfoundry.sipxconfig.common.event.UserGroupDeleteListener;
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.sipfoundry.sipxconfig.setting.Group;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

/**
 * ForwardingContextImpl
 * 
 */
public class ForwardingContextImpl extends HibernateDaoSupport implements ForwardingContext,
        ApplicationListener {

    private static final String PARAM_SCHEDULE_ID = "scheduleId";
    private static final String PARAM_USER_ID = "userId";
    private static final String PARAM_USER_GROUP_ID = "userGroupId";

    private CoreContext m_coreContext;

    private SipxReplicationContext m_replicationContext;

    /**
     * Looks for a call sequence associated with a given user.
     * 
     * This version just assumes that CallSequence id is the same as user id. More general
     * implementation would run a query. <code>
     *      String ringsForUser = "from CallSequence cs where cs.user = :user";
     *      hibernate.findByNamedParam(ringsForUser, "user", user);
     * </code>
     * 
     * @param user for which CallSequence object is retrieved
     */
    public CallSequence getCallSequenceForUser(User user) {
        return getCallSequenceForUserId(user.getId());
    }

    public void notifyCommserver() {
        // Notify commserver of ALIAS and AUTH_EXCEPTIONS
        m_replicationContext.generate(DataSet.ALIAS);
        m_replicationContext.generate(DataSet.AUTH_EXCEPTION);
    }

    public void saveCallSequence(CallSequence callSequence) {
        getHibernateTemplate().update(callSequence);
        // Notify commserver of ALIAS and AUTH_EXCEPTIONS
        notifyCommserver();
    }

    public void flush() {
        getHibernateTemplate().flush();
    }

    public CallSequence getCallSequenceForUserId(Integer userId) {
        HibernateTemplate hibernate = getHibernateTemplate();
        return (CallSequence) hibernate.load(CallSequence.class, userId);
    }

    public void removeCallSequenceForUserId(Integer userId) {
        CallSequence callSequence = getCallSequenceForUserId(userId);
        callSequence.clear();
        saveCallSequence(callSequence);
        getHibernateTemplate().flush();
    }

    public void removeSchedulesForUserID(Integer userId) {
        List schedules = getPersonalSchedulesForUserId(userId);
        getHibernateTemplate().deleteAll(schedules);
    }

    public Ring getRing(Integer id) {
        HibernateTemplate hibernate = getHibernateTemplate();
        return (Ring) hibernate.load(Ring.class, id);
    }

    public Collection getAliasMappings() {
        List aliases = new ArrayList();
        List sequences = loadAllCallSequences();
        for (Iterator i = sequences.iterator(); i.hasNext();) {
            CallSequence sequence = (CallSequence) i.next();
            aliases.addAll(sequence.generateAliases(m_coreContext.getDomainName()));
        }
        return aliases;
    }

    public List getForwardingAuthExceptions() {
        List aliases = new ArrayList();
        List sequences = loadAllCallSequences();
        for (Iterator i = sequences.iterator(); i.hasNext();) {
            CallSequence sequence = (CallSequence) i.next();
            User user = sequence.getUser();
            if (user.hasPermission(PermissionName.FORWARD_CALLS_EXTERNAL)) {
                aliases.addAll(sequence.generateAuthExceptions());
            }
        }

        return aliases;
    }

    /**
     * Loads call sequences for all uses in current root organization
     * 
     * @return list of CallSequence objects
     */
    private List loadAllCallSequences() {
        List sequences = getHibernateTemplate().find("from CallSequence cs");
        return sequences;
    }

    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    public void setReplicationContext(SipxReplicationContext replicationContext) {
        m_replicationContext = replicationContext;
    }

    public UserDeleteListener createUserDeleteListener() {
        return new OnUserDelete();
    }

    public void clear() {
        Collection sequences = loadAllCallSequences();
        for (Iterator i = sequences.iterator(); i.hasNext();) {
            CallSequence sequence = (CallSequence) i.next();
            sequence.clear();
            saveCallSequence(sequence);
        }
    }

    public UserGroupDeleteListener createUserGroupDeleteListener() {
        return new OnUserGroupDelete();
    }

    private class OnUserDelete extends UserDeleteListener {
        protected void onUserDelete(User user) {
            removeCallSequenceForUserId(user.getId());
            removeSchedulesForUserID(user.getId());
        }
    }

    private class OnUserGroupDelete extends UserGroupDeleteListener {
        protected void onUserGroupDelete(Group group) {
            List<UserGroupSchedule> schedules = getSchedulesForUserGroupId(group.getId());
            if (schedules != null && schedules.size() > 0) {
                // get all rings for the schedules, set all on always
                List<Ring> ringsToModify = new ArrayList<Ring>();
                for (UserGroupSchedule ugSchedule : schedules) {
                    List<Ring> rings = getRingsForScheduleId(ugSchedule.getId());
                    for (Ring ring : rings) {
                        // set schedule on always
                        ring.setSchedule(null);
                        ringsToModify.add(ring);
                    }
                }
                getHibernateTemplate().saveOrUpdateAll(ringsToModify);
                getHibernateTemplate().deleteAll(schedules);
                notifyCommserver();
            }
        }
    }

    public List<Schedule> getPersonalSchedulesForUserId(Integer userId) {
        HibernateTemplate hibernate = getHibernateTemplate();

        return hibernate.findByNamedQueryAndNamedParam("userSchedulesForUserId", PARAM_USER_ID,
                userId);
    }

    public List getRingsForScheduleId(Integer scheduleId) {
        HibernateTemplate hibernate = getHibernateTemplate();

        return hibernate.findByNamedQueryAndNamedParam("ringsForScheduleId", PARAM_SCHEDULE_ID,
                scheduleId);
    }

    public Schedule getScheduleById(Integer scheduleId) {
        return (Schedule) getHibernateTemplate().load(Schedule.class, scheduleId);
    }

    public void saveSchedule(Schedule schedule) {
        getHibernateTemplate().saveOrUpdate(schedule);
        // Notify commserver of ALIAS and AUTH_EXCEPTIONS
        notifyCommserver();
    }

    public List<Schedule> deleteSchedulesById(Collection<Integer> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            // no schedules to delete => nothing to do
            return null;
        }
        List<Schedule> schedulesWithoutRings = new ArrayList<Schedule>();
        List<Schedule> schedulesWithRings = new ArrayList<Schedule>();
        for (Integer id : scheduleIds) {
            Schedule schedule = getScheduleById(id);
            List rings = getRingsForScheduleId(schedule.getId());
            if (rings == null || rings.size() == 0) {
                schedulesWithoutRings.add(schedule);
            } else {
                schedulesWithRings.add(schedule);
            }
        }
        getHibernateTemplate().deleteAll(schedulesWithoutRings);
        return schedulesWithRings;
    }

    public List<UserGroupSchedule> getAllUserGroupSchedules() {
        return getHibernateTemplate().findByNamedQuery("allUserGroupSchedules");
    }

    public List<Schedule> getAllAvailableSchedulesForUser(User user) {
        List<Schedule> schedulesForUser = new ArrayList<Schedule>();
        schedulesForUser.addAll(getPersonalSchedulesForUserId(user.getId()));
        for (Group group : user.getGroups()) {
            schedulesForUser.addAll(getSchedulesForUserGroupId(group.getId()));
        }

        return schedulesForUser;
    }

    public List<UserGroupSchedule> getSchedulesForUserGroupId(Integer userGroupId) {
        HibernateTemplate hibernate = getHibernateTemplate();

        return hibernate.findByNamedQueryAndNamedParam("userSchedulesForUserGroupId",
                PARAM_USER_GROUP_ID, userGroupId);
    }

    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof DSTChangeEvent) {
            notifyCommserver();
        }
    }
}
