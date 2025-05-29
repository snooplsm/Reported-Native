import React from "react";
import { Dimensions, View, StyleSheet, Text } from "react-native";
import { Button, Icon } from "react-native-elements";
import moment from "moment";
import { IconStyle, HorizontalStyle } from "./Styles";
import { DateRange, MonthDayYear } from "./DateRange";

export default class CalendarView extends React.Component {
  constructor(props) {
    super(props);
    const s = {};
    s.mdy = new MonthDayYear();
    s.days = [
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10,
      11,
      12,
      13,
      14,
      15,
      16,
      17,
      18,
      19,
      20,
      21,
      22,
      23,
      24,
      25,
      26,
      27,
      28,
      29,
      [30, 31]
    ];

    s.months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
    const year = moment().year();
    s.years = [year, year - 1, year - 2, year - 3, year - 4, year - 5];
    this.state = s;
  }

  type(key) {
    const whatever = this.state[key];
    if (whatever) {
      return "outline";
    } else {
      return "clear";
    }
  }

  toggleDay(day) {
    this.toggle(`day`, day);
  }

  toggleMonth(day) {
    this.toggle(`month`, day);
  }

  toggleYear(year) {
    this.toggle(`year`, year);
  }

  toggle(key, val) {
    const kkey = `${key}_`;
    let days = Object.assign({}, this.state);
    const { mdy } = this.state;
    if (days[`${kkey}${val}`]) {
      days[`${kkey}${val}`] = false;
      if (key === "day") {
        mdy.day = null;
      } else if (key === "month") {
        mdy.month = null;
      } else if (key === "year") {
        mdy.year = null;
      }
    } else {
      Object.keys(days)
        .filter(x => x.startsWith(kkey))
        .forEach(x => {
          days[x] = false;
        });
      days[`${kkey}${val}`] = val;
      if (key === "day") {
        mdy.day = val;
      } else if (key === "month") {
        mdy.month = val;
      } else if (key === "year") {
        mdy.year = val;
      }
    }
    this.setState(days, () => this.check());
  }

  check() {
    this.setState({
      dateString: `Set ${this.state.mdy.allPossibleDates ?? ""}`.trim()
    });
  }

  _dayButton(day) {
    const typeButton = this.type(`day_${day}`);
    const number = typeof day === "number";
    let asString = day.toString();
    if (!number) {
      asString = day.join("/");
    }
    let containerStyle = null;
    if (day < 26) {
      containerStyle = style.dayItem;
    } else if (day < 30) {
      containerStyle = style.dayItem26;
    } else {
      containerStyle = style.dayItem30;
    }
    return (
      <Button
        key={`day_${day}`}
        type={typeButton}
        style={style.dayStyle}
        titleStyle={day < 26 ? style.dayText : style.dayText26}
        onPress={x => this.toggleDay(day)}
        containerStyle={containerStyle}
        title={`${asString}`}
      />
    );
  }

  render() {
    const months = moment.monthsShort();
    return (
      <View>
        <View style={style.iconContainer}>
          <Icon
            onPress={() => {
              const close = this.props.onClose ?? (() => {});
              close();
            }}
            containerStyle={IconStyle.close}
            color="#000000"
            name="close"
          />
        </View>
        <View style={style.container}>
          <View style={style.month}>
            {this.state.months.map(month => {
              return (
                <Button
                  key={`month_${month}`}
                  type={this.type(`month_${month}`)}
                  onPress={x => this.toggleMonth(month)}
                  style={style.monthStyle}
                  containerStyle={style.monthItem}
                  titleStyle={style.dayText}
                  title={`${months[month - 1]}`}
                />
              );
            })}
          </View>
          <View style={style.day}>
            {this.state.days.map(day => {
              return this._dayButton(day);
            })}
          </View>

          <View style={style.year}>
            {this.state.years.map(year => {
              return (
                <Button
                  key={`year_${year}`}
                  type={this.type(`year_${year}`)}
                  onPress={x => this.toggleYear(year)}
                  style={style.yearStyle}
                  containerStyle={style.yearItem}
                  titleStyle={style.dayText}
                  title={`${year}`}
                />
              );
            })}
          </View>
        </View>
        <Button
          onPress={() => {
            if (this.state.mdy.valid) {
              const onValidDate = this.props.onValidDate ?? (() => {});
              onValidDate(this.state.mdy);
            } else {
              const onInvalidDate = this.props.onInValidDate ?? (() => {});
              onInvalidDate(this.state.mdy);
            }
          }}
          type="outline"
          buttonStyle={{
            borderRadius: 0,
            height: 80
          }}
          title={this.state.dateString ?? "Set"}
        />
      </View>
    );
  }
}

const { width: screenWidth, height: screenheight } = Dimensions.get("screen");

const fontSize = screenWidth <= 375 ? 15 : 17;

const style = StyleSheet.create({
  iconContainer: {
    width: "100%",
    height: 100,
    flexDirection: "row",
    flexWrap: "nowrap",
    justifyContent: "flex-end"
  },
  container: {
    width: "100%",
    flexDirection: "row"
  },
  month: {
    width: "25%",
    flexWrap: "wrap",
    flexDirection: "row"
  },
  monthItem: {
    width: "50%"
  },
  monthStyle: {},
  day: {
    width: "55%",
    flexWrap: "wrap",
    flexDirection: "row"
  },

  dayText: {
    fontSize: fontSize
  },
  dayText26: {
    fontSize: fontSize - 1
  },

  dayItem: {
    width: "19%"
  },
  dayItem26: {
    width: "17%"
  },
  dayItem30: {
    width: "29%"
  },
  dayStyle: {
    margin: 0
  },
  year: {
    width: "15%",
    flexWrap: "wrap",
    flexDirection: "row"
  },
  yearStyle: {},
  yearItem: {
    width: "100%"
  }
});
