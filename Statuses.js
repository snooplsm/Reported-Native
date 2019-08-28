export const statuses = [
  {
    objectId: "DFxgbcfD20",
    sId: 3,
    createdAt: "2016-11-03T16:56:43.908Z",
    updatedAt: "2016-11-03T17:12:32.810Z",
    text: "Hearing Scheduled",
    key: "HEARING",
    enable: true
  },
  {
    objectId: "MxyyOSyrln",
    sId: 7,
    createdAt: "2016-11-03T17:12:51.272Z",
    updatedAt: "2016-11-04T11:04:48.523Z",
    enable: true,
    key: "ARCHIVE",
    text: "No Reason / Archive"
  },
  {
    objectId: "a67IpF4Blh",
    sId: 4,
    createdAt: "2016-11-03T16:56:50.242Z",
    updatedAt: "2016-11-03T18:30:37.374Z",
    text: "Driver Paid Fine / Guilty",
    key: "GUILTY",
    enable: true
  },
  {
    objectId: "bAmcgJtAgp",
    text: "Summons Issued",
    key: "SUMMONS",
    enable: true,
    createdAt: "2016-11-03T16:55:51.318Z",
    updatedAt: "2016-11-03T17:35:37.230Z",
    sId: 2
  },
  {
    objectId: "mIary5x1No",
    sId: 6,
    createdAt: "2016-11-03T16:58:09.899Z",
    updatedAt: "2016-11-04T11:04:47.366Z",
    text: "Driver Not Guilty",
    key: "NOT_GUILTY",
    enable: true
  },
  {
    objectId: "mtJ8ICLCe0",
    sId: 5,
    createdAt: "2016-11-03T16:56:54.348Z",
    updatedAt: "2016-11-04T11:04:46.136Z",
    text: "Unable to ID Driver",
    key: "UNABLE_TO_ID",
    enable: true
  },
  {
    sId: -1,
    createdAt: "2016-11-03T16:56:54.348Z",
    updatedAt: "2016-11-04T11:04:46.136Z",
    text: "Error Pending",
    key: "ERROR_PENDING",
    enable: true
  },
  {
    sId: -3,
    createdAt: "2016-11-03T16:56:54.348Z",
    updatedAt: "2016-11-04T11:04:46.136Z",
    text: "Processing",
    key: "Pre_PROCESSING",
    enable: true
  },
  {
    sId: -2,
    createdAt: "2016-11-03T16:56:54.348Z",
    updatedAt: "2016-11-04T11:04:46.136Z",
    text: "Processing",
    key: "POST_PROCESSING",
    enable: true
  },
  {
    sId: 1,
    createdAt: "2016-11-03T16:56:54.348Z",
    updatedAt: "2016-11-04T11:04:46.136Z",
    text: "Submitted",
    key: "SUBMITTED",
    enable: true
  }
];

export const statusesMap = statuses.reduce((map, obj) => {
  map[obj.sId] = obj;
  return map;
}, {});
