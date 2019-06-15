import React from "react";

import {
  StyleSheet,
  Text,
  View,
  SafeAreaView,
  FlatList,
  Button
} from "react-native";
import { Icon } from "react-native-elements";
import { api, uploadFile } from "./Api";
import { colors } from "./Styles";
import ReportView from "./ReportView";
import SubmissionFilter from "./SubmissionFilter";

export default class Submissions extends React.Component {
  static navigationOptions = ({ navigation }) => {
    return {
      title: "Reported",
      headerRight: (
        <Icon
          onPress={navigation.getParam("filterPressed")}
          name="filter-list"
          title="Filter"
        />
      )
    };
  };

  _filterPressed = () => {
    this.setState({ submissionsFilter: true });
  };

  constructor(props) {
    super(props);
    this.state = {
      reports: [],
      count: 0,
      filter: null
    };
  }

  submissionsFilter() {
    if (this.state.submissionsFilter) {
      return (
        <SubmissionFilter
          filter={this.state.filter}
          onFilterPressed={filter => {
            console.log(filter);
            this.setState({ filter: filter, submissionsFilter: false }, () => {
              this.fetchReports();
            });
          }}
          onCloseClicked={() => this.setState({ submissionsFilter: false })}
        />
      );
    } else {
      return <></>;
    }
  }

  componentDidMount() {
    this.props.navigation.setParams({ filterPressed: this._filterPressed });
    this.fetchReports();
  }

  fetchReports() {
    api
      .reports(this.state.filter)
      .then(res => {
        const addressMap = res.data.addresses.reduce((map, x) => {
          map[x.id] = x;
          return map;
        }, {});
        const reports = [...res.data.reports].map((report, index) => {
          return {
            report: report,
            address: addressMap[report.addressId] ?? { street: "unknown" }
          };
        });
        this.setState({ reports });
      })
      .catch(err => {
        console.log(err);
        alert("An error occurred");
      });
  }

  render() {
    return (
      <View>
        <FlatList
          data={this.state.reports}
          renderItem={({ item }) => {
            const { report, address } = item;
            return <ReportView key={report.id} report={item} />;
          }}
        />
        {this.submissionsFilter()}
      </View>
    );
  }
}
