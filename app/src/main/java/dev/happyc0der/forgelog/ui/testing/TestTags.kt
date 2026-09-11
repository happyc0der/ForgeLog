package dev.happyc0der.forgelog.ui.testing

/**
 * Stable handles for UI tests.
 *
 * Tags are used instead of matching on display text so that rewording a string, or localising it,
 * cannot break a test. Only elements a test needs to find are tagged — tagging everything makes the
 * list meaningless.
 */
object TestTags {
    const val HOME_SCREEN = "home_screen"
    const val HOME_GREETING = "home_greeting"
    const val HOME_PRIMARY_ACTION = "home_primary_action"
    const val HOME_RESUME_ACTION = "home_resume_action"
    const val HOME_LAST_WORKOUT = "home_last_workout"
    const val HOME_WEEK_SUMMARY = "home_week_summary"
    const val HOME_ACTION_ADHOC = "home_action_adhoc"
    const val HOME_ACTION_CREATE_PROGRAM = "home_action_create_program"

    const val HISTORY_SCREEN = "history_screen"
    const val HISTORY_LIST = "history_list"
    const val HISTORY_SEARCH = "history_search"
    const val HISTORY_FILTERS = "history_filters"
    const val SESSION_DETAIL_SCREEN = "session_detail_screen"

    const val ANALYTICS_SCREEN = "analytics_screen"
    const val ANALYTICS_RANGE_PICKER = "analytics_range_picker"

    const val SETTINGS_SCREEN = "settings_screen"
    const val SETTINGS_EXPORT_JSON = "settings_export_json"
    const val SETTINGS_IMPORT_JSON = "settings_import_json"
    const val SETTINGS_EXPORT_CSV = "settings_export_csv"
    const val SETTINGS_DELETE_ALL = "settings_delete_all"

    const val START_WORKOUT_SCREEN = "start_workout_screen"
    const val START_WORKOUT_CONFIRM = "start_workout_confirm"
    const val ACTIVE_WORKOUT_SCREEN = "active_workout_screen"
    const val ACTIVE_WORKOUT_FINISH = "active_workout_finish"
    const val ACTIVE_WORKOUT_FINISH_CONFIRM = "active_workout_finish_confirm"
    const val ACTIVE_WORKOUT_ADD_SET = "active_workout_add_set"

    const val WORKOUT_SUMMARY_SCREEN = "workout_summary_screen"
    const val WORKOUT_SUMMARY_RECORDS = "workout_summary_records"
    const val WORKOUT_SUMMARY_DONE = "workout_summary_done"

    const val PROGRAMS_SCREEN = "programs_screen"
    const val PROGRAM_DETAIL_SCREEN = "program_detail_screen"
    const val PROGRAM_DAY_BUILDER_SCREEN = "program_day_builder_screen"

    /** Drag handle for reorderable rows; suffixed with the row's stable id. */
    fun dragHandle(id: Any): String = "drag_handle_$id"
}
