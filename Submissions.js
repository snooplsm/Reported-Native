import React from "react";

import {
  Alert,
  StyleSheet,
  Text,
  View,
  SafeAreaView,
  FlatList,
  Button,
  TouchableOpacity
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
          iconStyle={{
            padding: 10
          }}
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
      filter: null,
      refreshing: true
    };
  }

  get submissionsFilter() {
    if (this.state.submissionsFilter) {
      return (
        <SubmissionFilter
          style={{
            top: 0,
            bottom: 0,
            left: 0,
            right: 0,
            position: "absolute",
            backgroundColor: "white",
            zIndex: 9999
          }}
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
    this.setState({ refreshing: true });
    api
      .reports(this.state.filter)
      .then(res => {
        this.setState({ refreshing: false });
        const addressMap = res.data.addresses.reduce((map, x) => {
          map[x.id] = x;
          return map;
        }, {});
        const reports = [...res.data.reports].map((report, index) => {
          return {
            report: report,
            address: addressMap[report.addressId] ?? { street: "" }
          };
        });
        console.log(reports);
        this.setState({ reports });
      })
      .catch(err => {
        this.setState({ refreshing: false });
        console.log(err);
        alert("An error occurred");
      });
  }

  deleteReport(report) {
    api
      .deleteReport(report.id)
      .then(cancelled => {
        console.log("deleted");
        const reports = this.state.reports.filter(x => {
          console.log(x);
          console.log(report.id);
          return x.report.id != report.id;
        });
        this.setState({
          reports: reports
        });
      })
      .catch(e => {
        console.log(e);
        alert("There was a problem.");
      });
  }

  _keyExtractor = (item, index) => item.report.id;

  _onRefresh = () => {
    this.fetchReports();
  };

  render() {
    return (
      <>
        {this.submissionsFilter}
        <View>
          <FlatList
            keyExtractor={this._keyExtractor}
            onRefresh={this._onRefresh}
            refreshing={this.state.refreshing}
            data={this.state.reports}
            renderItem={({ item }) => {
              const { report, address } = item;
              return (
                <TouchableOpacity
                  onLongPress={() => {
                    if (report.status > 0) {
                      return;
                    }
                    Alert.alert(
                      "Confirm Delete?",
                      "Are you sure you want to delete this report?",
                      [
                        { text: "Cancel", style: "cancel" },
                        { text: "Ok", onPress: () => this.deleteReport(report) }
                      ]
                    );
                  }}
                >
                  <ReportView report={item} />
                </TouchableOpacity>
              );
            }}
          />
        </View>
      </>
    );
  }
}
