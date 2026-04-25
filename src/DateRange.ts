import moment, { type Moment, type MomentInput } from "moment";

export class DateRange {
  start: Moment;
  end: Moment;

  constructor(date?: MomentInput, end?: MomentInput) {
    this.start = moment(date ?? new Date()).startOf("day");
    this.end = moment(end ?? date ?? new Date()).endOf("day");
  }
}

export class MonthDayYear {
  private _month?: number;
  day?: number;
  year?: number;

  constructor(month?: number, day?: number, year?: number) {
    this._month = month;
    this.day = day;
    this.year = year;
  }

  set month(value: number | undefined) {
    this._month = value;
  }

  get month() {
    if (this._month) {
      return this._month - 1;
    }
    return this._month;
  }

  get valid() {
    const years = [this.month, this.day, this.year];
    return years.filter(x => x).length == years.length;
  }

  get date() {
    return moment()
      .year(this.year ?? 1)
      .month(this.month ?? 0)
      .date(this.day ?? 1);
  }

  get allPossibleDates() {
    let month = this.month;
    const year = this.year;
    const day = this.day;
    if (month != null && year != null && day != null) {
      return this.date.format("MMMM Do YYYY");
    } else if (month != null && year != null) {
      const mm = moment().month(month);
      return `${mm.format("MMMM")} ${year}`;
    } else if (month != null && day != null) {
      const mm = moment().month(month);
      return `${mm.format("MMMM")} ${day} ${mm.year() - 5}-${mm.year()}`;
    } else if (month != null) {
      const mm = moment().month(month);
      return `${mm.format("MMMM")} ${mm.year() - 5}-${mm.year()}`;
    } else if (year != null) {
      return `${year}`;
    } else {
      return null;
    }
  }

  range(start: number, stop: number, step = 1) {
    const a = [start];
    let b = start;
    while (step > 0 ? b < stop : b > stop) {
      a.push((b += step));
    }
    return a;
  }

  get allDates() {
    const month = this.month;
    const year = this.year;
    const day = this.day;
    if (month != null && year != null && day != null) {
      return [new DateRange(this.date)];
    }
    if (month != null && year != null) {
      const mm = moment()
        .year(year)
        .month(month);
      const monthStart = mm.startOf("month");
      const monthEnd = mm.endOf("month");
      return [new DateRange(monthStart, monthEnd)];
    } else if (month != null && day != null) {
      return this.range(moment().year(), moment().year() - 5, -1).map(year => {
        const mm = moment()
          .year(year)
          .month(month);
        return new DateRange(mm.startOf("month"), mm.endOf("month"));
      });
    } else if (month != null) {
      return this.range(moment().year(), moment().year() - 5, -1).map(year => {
        const mm = moment().year(year);
        return new DateRange(mm.startOf("year"), mm.endOf("year"));
      });
    } else if (year != null) {
      const mm = moment().year(year);
      const ms = moment(mm);
      const me = moment(mm);
      return [new DateRange(ms.startOf("year"), me.endOf("year"))];
    } else {
      return [new DateRange()];
    }
  }
}
