import org.sonatype.nexus.scheduling.TaskConfiguration
import org.sonatype.nexus.scheduling.TaskInfo
import org.sonatype.nexus.scheduling.TaskNotificationCondition
import org.sonatype.nexus.scheduling.TaskScheduler
import org.sonatype.nexus.scheduling.schedule.Monthly
import org.sonatype.nexus.scheduling.schedule.Schedule
import org.sonatype.nexus.scheduling.schedule.Weekly

import java.text.SimpleDateFormat

class SyncScheduledTasks extends ScriptBaseClass {

    private final TaskScheduler taskScheduler

    SyncScheduledTasks(context) {
        super(context)
        this.taskScheduler = container.lookup(TaskScheduler)
    }

    TaskConfiguration configTask(TaskInfo taskInfo = null, Map taskDef) {
        TaskConfiguration taskConfiguration

        if (taskInfo) {
            taskConfiguration = taskInfo.getConfiguration()
        } else if (taskDef.typeId == null) {
            throw new Exception('Task requires a typeId parameter')
        } else {
            taskConfiguration = taskScheduler.createTaskConfigurationInstance(taskDef.typeId as String)
        }

        taskConfiguration.with {
            enabled = Boolean.parseBoolean(taskDef.enabled as String)
            name = taskDef.name
            message = taskDef.get('message', '')
            alertEmail = taskDef.get('alertEmail', '')
        }

        taskConfiguration.setNotificationCondition(
                taskDef.notificationCondition as TaskNotificationCondition ?: TaskNotificationCondition.DEFAULT)

        // Set extra properties like 'language' or 'source'
        taskDef?.properties?.each { key, value -> taskConfiguration.setString(key as String, value as String) }
        return taskConfiguration
    }

    Schedule configSchedule(Map taskDef) {
        String scheduleType = taskDef.schedule?.type ?: 'MANUAL'
        String cron = taskDef.frequency?.cron

        // Start date time. Defaults to now. Unused for manual, now and cron schedules
        // This is our expected date format
        SimpleDateFormat dateFormat = new SimpleDateFormat('yyyy-MM-dd\'T\'HH:mm:ss')
        String dateTimeString = taskDef.frequency?.startDateTime
        Date startDate = dateTimeString ? dateFormat.parse(dateTimeString) : new Date()

        // List of weekdays to run task. Used for weekly schedule. Need at least one
        // defaults to null which will error if this schedule is chosen
        def weeklyDays = taskDef.frequency?.weeklyDays

        // List of calendar days to run task. Used for monthly schedule. Need at leas one
        // defaults to null which will error if this schedule is chosen
        def monthlyDays = taskDef.frequency?.monthlyDays

        def schedule

        switch (scheduleType.toUpperCase()) {
            case 'MANUAL':
                schedule = taskScheduler.scheduleFactory.manual()
                break
            case 'NOW':
                schedule = taskScheduler.scheduleFactory.now()
                break
            case 'ONCE':
                schedule = taskScheduler.scheduleFactory.once(startDate)
                break
            case 'HOURLY':
                schedule = taskScheduler.scheduleFactory.hourly(startDate)
                break
            case 'DAILY':
                schedule = taskScheduler.scheduleFactory.daily(startDate)
                break
            case 'WEEKLY':
                if (!weeklyDays) throw new Exception('Weekly schedule requires a weeklyDays list parameter')

                Set<Weekly.Weekday> weekdays = []
                weeklyDays.each { String day -> weekdays.add(Weekly.Weekday.valueOf(day)) }

                schedule = taskScheduler.scheduleFactory.weekly(startDate, weekdays)
                break
            case 'MONTHLY':
                if (!monthlyDays) throw new Exception('Monthly schedule requires a monthlyDays list parameter')

                Set<Monthly.CalendarDay> calendarDays = []
                monthlyDays.each { String day -> calendarDays.add(Monthly.CalendarDay.day(day as Integer)) }

                schedule = taskScheduler.scheduleFactory.monthly(startDate, calendarDays)
                break
            case 'CRON':
                schedule = taskScheduler.scheduleFactory.cron(new Date(), cron)
                break
            default:
                throw new Exception('Unknown schedule type: ' + scheduleType)
                break
        }

        return schedule
    }

    void createTask(Map taskDef, Map action) {
        String name = taskDef.name
        String typeId = taskDef.typeId

        try {
            TaskConfiguration taskConfiguration = configTask(taskDef)
            Schedule schedule = configSchedule(taskDef)
            taskScheduler.scheduleTask(taskConfiguration, schedule)
            log.info('Task \'{} [{}]\' created', name, typeId)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('Cannot create task \'{} [{}]\': {}', name, typeId, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void updateTask(TaskInfo taskInfo, Map taskDef, Map action) {
        String name = taskInfo.getName()
        String typeId = taskInfo.getTypeId()

        try {
            TaskConfiguration taskConfiguration = configTask(taskInfo, taskDef)
            Schedule schedule = configSchedule(taskDef)
            taskScheduler.scheduleTask(taskConfiguration, schedule)
            log.info('Task \'{} [{}]\' updated', name, typeId)
            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)

        } catch (Exception e) {
            log.error('Cannot update task \'{} [{}]\': {}', name, typeId, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deleteTask(TaskInfo taskInfo, Map action) {
        def name = taskInfo.getName()
        def typeId = taskInfo.getTypeId()
        try {
            taskInfo.remove()
            log.info('Task \'{} [{}]\' deleted', name, typeId)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)
        } catch (Exception ex) {
            log.info('Cannot delete task \'{} [{}]\'', name, typeId)
            scriptResult.addActionDetails(action, ex)
        }
    }

    String execute() {
        Boolean deleteUnknownTasks = (config?.nexus?.deleteUnknownItems?.tasks) ?: false
        List<Map<String, Object>> taskDefs = (config?.tasks as List<Map<String, Object>>) ?: []

        // Delete unknown existing blob stores
        if (deleteUnknownTasks) {
            taskScheduler.listsTasks().each { taskInfo ->
                def name = taskInfo.getName()
                def typeId = taskInfo.getTypeId()

                def action = scriptResult.newAction(name: name, typeId: typeId)

                Boolean found = taskDefs.any { it.name == name && it.typeId == typeId }

                if (found) {
                    log.info('Task \'{} [{}]\' found. Left untouched', name, typeId)
                    scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

                } else {
                    deleteTask(taskInfo, action)
                }
            }
        }

        // Create or update tasks
        taskDefs.each { taskDef ->
            String name = taskDef.name
            String typeId = taskDef.typeId
            def action = scriptResult.newAction(name: name, typeId: typeId)

            def existingTask = taskScheduler.listsTasks().find { it.name == name && it.typeId == typeId }

            if (existingTask) {
                updateTask(existingTask, taskDef, action)
            } else {
                createTask(taskDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncScheduledTasks(syncScheduledTasks).execute()
