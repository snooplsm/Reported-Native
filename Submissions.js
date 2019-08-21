import React from "react";

import {
  Alert,
  StyleSheet,
  Text,
  View,
  SafeAreaView,
  Button,
  TouchableOpacity
} from "react-native";
import { Icon } from "react-native-elements";
import ListSpoof from "./ListSpoof";
import LogoTitle from "./LogoTitle";
import { api, uploadFile } from "./Api";
import { colors } from "./Styles";
import ReportView from "./ReportView";
import moment from "moment";
import SubmissionFilter from "./SubmissionFilter";

export default class Submissions extends React.Component {
  static navigationOptions = ({ navigation }) => {
    return {
      headerTitle: <LogoTitle />,
      headerLeft: () => {
        return (
          <Icon
            isVisible={false}
            containerStyle={{ padding: 10, opacity: 0 }}
            name="arrow-back"
            color="#000"
          />
        );
      },
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
      sections: [],
      count: 0,
      filter: null,
      refreshing: true,
      lastResultEmpty: false,
      error: undefined
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

  fetchReports(offset) {
    this.setState({ refreshing: true });
    api
      .reports(this.state.filter, offset)
      .then(res => {
        this.setState({ refreshing: false, error: undefined });
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

        const lastResultEmpty = reports.length < 100;
        const combinedReports = this.state.reports.concat(reports);
        const dict = {};
        reports.forEach(x => {
          const date = moment(x.report.timeofincident).startOf("month");
          const list = dict[date.valueOf()] ?? [];
          list.push(x);
          dict[date.valueOf()] = list;
        });
        const sections = Object.keys(dict).map(key => {
          const list = dict[key];
          const date = moment(Number(key));
          return {
            title: date.format("MMMM YYYY"),
            data: list
          };
        });
        this.setState({
          reports: combinedReports,
          sections: sections || [],
          lastResultEmpty
        });
      })
      .catch(err => {
        let message = "";
        if (err.response) {
          if (err.response.status == 401) {
            message = "Credentials not found";
          } else {
            message = err.response.data.message;
          }
        } else if (err.request) {
          message = "Server was unresponsive";
        } else {
          messaage = "Unknown error";
        }
        this.setState({ refreshing: false, error: message });
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
        let message = "";
        if (x.response) {
          if (x.response.status == 401) {
            message = "Credentials not found";
          } else {
            message = x.response.data.message;
          }
        } else if (x.request) {
          message = "Server was unresponsive";
        } else {
          messaage = "Unknown error";
        }
        alert("Problem deleting report");
      });
  }

  _keyExtractor = (item, index) => item.report.id;

  _onRefresh = () => {
    this.fetchReports();
  };

  _onEndReached = info => {
    if (this.state.refreshing) {
      return;
    }
    if (this.state.reports.length == 0) {
      return;
    }
    if (this.state.lastResultEmpty) {
      return;
    }
    alert(this.state.reports.length);
    this.fetchReports(this.state.reports.length);
  };

  render() {
    return (
      <>
        {this.submissionsFilter}
        <View>
          <ListSpoof
            style={{
              width: "100%",
              height: "100%"
            }}
            renderSectionHeader={({ section: { title } }) => (
              <Text
                style={{
                  width: "100%",
                  backgroundColor: "#FAFAFA",
                  textAlign: "center",
                  fontWeight: "bold",
                  fontSize: 19,
                  padding: 10
                }}
              >
                {title}
              </Text>
            )}
            keyExtractor={this._keyExtractor}
            sections={this.state.sections}
            onRefresh={this._onRefresh}
            refreshing={this.state.refreshing}
            data={this.state.reports}
            onEndReached={this._onEndReached}
            initialNumToRender={2}
            ListFooterComponent={<View style={{ height: 10 }} />}
            renderItem={({ item }) => {
              const { report, address } = item;
              console.log(report);
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
          {this.state.refreshing && this.state.reports.length === 0 && (
            <View
              style={{
                position: "absolute",
                width: "100%",
                height: "100%",
                justifyContent: "center",
                alignItems: "center",
                flex: 1
              }}
            >
              {this.state.refreshing && <Text style>Loading...</Text>}
              {!this.state.refreshing && this.state.reports.length === 0 && (
                <Text>No reports found.</Text>
              )}
            </View>
          )}
          {this.state.error && this.state.reports.length === 0 && (
            <View
              style={{
                position: "absolute",
                width: "100%",
                height: "100%",
                justifyContent: "center",
                alignItems: "center",
                flex: 1
              }}
            >
              <TouchableOpacity onPress={() => this.fetchReports()}>
                <Icon name="refresh" size={100} />
                <Text
                  style={{ textAlign: "center" }}
                >{`${this.state.error}\n\nTap to retry`}</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      </>
    );
  }
}
