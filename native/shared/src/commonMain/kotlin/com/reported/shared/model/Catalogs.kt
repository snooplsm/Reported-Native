package com.reported.shared.model

object Catalogs {
    val complaintCategories: List<ComplaintCategory>
        get() = RemoteConfigOverrides.complaintCategories ?: defaultComplaintCategories

    val reportStatuses: List<ReportStatus>
        get() = RemoteConfigOverrides.reportStatuses ?: defaultReportStatuses

    val defaultComplaintCategories: List<ComplaintCategory> = listOf(
        ComplaintCategory("Z8vjWz8uYr", "Blocked bike lane", "cyclist, walker, pedestrian, passenger", "Blocked the bike lane"),
        ComplaintCategory("GzRxlMN1vl", "Blocked crosswalk", "cyclist, walker, pedestrian, passenger", "Blocked the crosswalk"),
        ComplaintCategory("X0dD3EB1Ym", "Drove aggressively", "cyclist, walker, pedestrian, passenger"),
        ComplaintCategory("WstZSmJr4t", "Drove recklessly", "passenger", "Drove recklessly"),
        ComplaintCategory("tpMiIrIuCe", "Failed to yield", "cyclist, walker, pedestrian"),
        ComplaintCategory("wm7Yim3Pc5", "Honked horn (no emergency)", "cyclist, walker, pedestrian"),
        ComplaintCategory("0iEd9qaziB", "Parked illegally", "cyclist, walker, pedestrian, passenger", "Parked illegally"),
        ComplaintCategory("DauBz1MDhJ", "Ran a red light or stop sign", "cyclist, walker, pedestrian, passenger", "Ran a red light or stop sign"),
        ComplaintCategory("XSIsLAVA2f", "Used phone while driving", "cyclist, walker, pedestrian, passenger"),
        ComplaintCategory("lVwiCXEK7G", "Was on a cell phone", "passenger"),
        ComplaintCategory("dJwrDRrD47", "Was speeding", "cyclist, walker, pedestrian, passenger")
    )

    val protectedReportStatuses: List<ReportStatus> = listOf(
        ReportStatus(0, "PENDING", "Pending"),
        ReportStatus(1, "SUBMITTED", "Submitted")
    )

    val defaultReportStatuses: List<ReportStatus> = listOf(
        ReportStatus(0, "PENDING", "Pending"),
        ReportStatus(1, "SUBMITTED", "Submitted"),
        ReportStatus(3, "HEARING", "Hearing Scheduled"),
        ReportStatus(7, "NO_REASON_ARCHIVE", "No Reason / Archive"),
        ReportStatus(4, "GUILTY", "Driver Paid Fine / Guilty"),
        ReportStatus(2, "SUMMONS", "Summons Issued"),
        ReportStatus(6, "NOT_GUILTY", "Driver Not Guilty"),
        ReportStatus(5, "UNABLE_TO_ID", "Unable to ID Driver"),
        ReportStatus(-1, "ERROR_PENDING", "Error Pending"),
        ReportStatus(-3, "PRE_PROCESSING", "Processing"),
        ReportStatus(-2, "POST_PROCESSING", "Processing")
    )
}
