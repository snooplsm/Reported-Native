import React from "react";

import {
    Alert,
    StyleSheet,
    Text,
    View,
    Modal,
    TouchableHighlight,
    TouchableOpacity,
    FlatList
} from "react-native";
import { Button, Card, Input, Icon, Overlay } from "react-native-elements";
import ListSpoof from "./ListSpoof";
import LogoTitle from "./LogoTitle";
import { api, uploadFile } from "./Api";
import * as DocumentPicker from "expo-document-picker";
import ReportView from "./ReportView";
import moment from "moment";
// import Swipeout from "react-native-swipeout";
import SubmissionFilter from "./SubmissionFilter";
import { statuses } from "./Statuses";

type ReportListItem = {
    report: any;
    address: any;
};

type SubmissionsState = {
    reports: ReportListItem[];
    sections: any[];
    count: number;
    filter?: any;
    refreshing: boolean;
    lastResultEmpty: boolean;
    error?: string;
    offset?: number;
    submissionsFilter?: boolean;
    aggregate?: any;
    selectedRow?: ReportListItem;
    verdict?: string;
    results?: any[];
};

const TypedCard = Card as React.ComponentType<any>;
const TypedIcon = Icon as React.ComponentType<any>;
const TypedListSpoof = ListSpoof as React.ComponentType<any>;
const TypedOverlay = Overlay as React.ComponentType<any>;

export default class Submissions extends React.Component<any, SubmissionsState> {
    static navigationOptions = ({ navigation }: { navigation: any }) => {
        return {
            headerTitle: <LogoTitle />,
            headerLeft: () => {
                return (
                    <TypedIcon
                        isVisible={false}
                        containerStyle={{ padding: 10, opacity: 0 }}
                        name="arrow-back"
                        color="#000"
                    />
                );
            },
            headerRight: (
                <TypedIcon
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

    private _forUser: boolean;

    constructor(props: any) {
        super(props);
        this._forUser = true;
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

    get offset() {
        return this.state.offset || this.state.reports.length;
    }

    get submissionsFilter() {
        if (this.state.submissionsFilter) {
            return (
                <Modal>
                    <SubmissionFilter
                        filter={this.state.filter}
                        onFilterPressed={filter => {
                            this.setState(
                                { filter: filter, submissionsFilter: false, reports: [] },
                                () => {
                                    this.fetchReports();
                                }
                            );
                        }}
                        onCloseClicked={() => this.setState({ submissionsFilter: false })}
                    />
                </Modal>
            );
        } else {
            return <></>;
        }
    }

    componentDidMount() {
        this.props.navigation.setParams({ filterPressed: this._filterPressed });
        this.fetchReports();
        this.fetchAggregate();
    }

    fetchAggregate() {
        api.reportStats().then(f => {
            const { sections } = this.state;
            if (sections.length > 0) {
                const section = sections[0];
                section.aggregate = this.state.aggregate;
            }
            this.setState({ sections });
        });
    }

    fetchReports(offset?: number) {
        this.setState({ refreshing: true });

        api
            .reports(this.state.filter, offset)
            .then(res => {
                this.setState({ refreshing: false, error: undefined });
                const addressMap = res.data.addresses.reduce((map: Record<string, any>, x: any) => {
                    map[x.id] = x;
                    return map;
                }, {});
                const seen = {};
                const reports = [...res.data.reports].map((report: any) => {
                    return {
                        report: report,
                        address: addressMap[report.addressId] ?? { street: "" }
                    };
                });

                const lastResultEmpty = reports.length < 100;
                const combinedReports = this.state.reports.concat(reports);
                const dict: Record<string, ReportListItem[]> = {};
                reports.forEach((x: ReportListItem) => {
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
                    offset: undefined,
                    lastResultEmpty
                });
            })
            .catch((err: any) => {
                console.log(err);
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
                    message = "Unknown error";
                }
                this.setState({ refreshing: false, error: message });
            });
    }

    deleteReport(report: any) {
        api
            .deleteReport(report.id)
            .then(cancelled => {
                const reports = this.state.reports.filter(x => {
                    return x.report.id != report.id;
                });
                this.setState({
                    reports: reports
                });
            })
            .catch((e: any) => {
                let message = "";
                if (e.response) {
                    if (e.response.status == 401) {
                        message = "Credentials not found";
                    } else {
                        message = e.response.data.message;
                    }
                } else if (e.request) {
                    message = "Server was unresponsive";
                } else {
                    message = "Unknown error";
                }
                Alert.alert("Problem deleting report", message);
            });
    }

    get _filterData() {
        const data: string[] = [];
        const filter = this.state.filter;
        if (!filter) {
            return data;
        }
        filter.keywords && filter.keywords.split(/[ ,]+/).map(x => data.push(x));
        filter.srid &&
            filter.srid.trim().length > 0 &&
            data.push(`311 SR: ${filter.srid.trim()}`);
        filter.complaints.map(x => data.push(x.name));
        filter.when &&
            filter.when.allPossibleDates &&
            data.push(filter.when.allPossibleDates);
        filter.location && data.push(`near: ${filter.location.formatted_address}`);
        return data.filter(x => x && x.trim().length > 0).map(x => x.trim());
    }

    changeStatus(report: any, status: string) {
        api
            .changeStatus({
                id: report.id,
                status: status
            })
            .then(success => {
                const newStatus = statuses.find(s => {
                    // console.log(s.key, status);
                    return s.key === status;
                });
                // console.log("wtf", newStatus);
                report.status = newStatus?.sId;
                this.setState({ selectedRow: null, verdict: undefined }, () => {
                    setTimeout(() => {
                        Alert.alert(
                            "Status Changed!",
                            `The status has been updated to ${newStatus?.text}`
                        );
                    }, 300);
                });
            })
            .catch(e => {
                // console.log("error changing status", e);
            });
    }

    guiltyNotGuilty(report: any, status: string) {
        this.setState({ verdict: status });
    }

    uploadAndChangeStatus(report: any, file: any, status: string) {
        let type = status;
        file.width = -1;
        file.height = -1;
        (uploadFile as any)(file)
            .then(media => {
                (media as any).type = `S3_STATUS_${type}`;
                return api.changeStatus({
                    id: report.id,
                    status: type,
                    media: media
                });
            })
            .then(yass => {
                const newStatus = statuses.find(key => key.key === status);
                // console.log("newStatus", newStatus);
                report.status = newStatus?.sId;
                report.fine = (type as any).fine;
                report.points = (type as any).points;
                this.setState({ selectedRow: undefined, verdict: undefined }, () => {
                    setTimeout(() => {
                        Alert.alert(
                            "Success",
                            `Your report has changed to ${newStatus?.text}`
                        );
                    }, 300);
                });
            })
            .catch(err => {
                // console.log(err);
                alert("There was an error");
            });
    }

    get notGuiltyOverlay() {
        if (
            !this.state.selectedRow ||
            ["NOT_GUILTY", "GUILTY"].indexOf(this.state.verdict) < 0
        ) {
            return <></>;
        }
        const { report, address } = this.state.selectedRow;
        return (
            <TypedOverlay
                isVisible={true}
                containerStyle={{ paddingLeft: 50, paddingRight: 100 }}
                height="auto"
                onBackdropPress={() => this.setState({ selectedRow: undefined })}
            >
                <View
                    style={{
                        flexDirection: "column"
                    }}
                >
                    <Text>
                        {this.state.verdict === "NOT_GUILTY" &&
                            `Please submit the TLC not guilty verdict that was emailed to you. (email subject: tlc notice of decision ${report.license.plate.substring(
                                0
                            )})`}
                        {this.state.verdict === "GUILTY" &&
                            `Please submit the TLC guilty verdict that was emailed to you. (email subject: tlc notice of decision ${report.license.plate.substring(
                                0
                            )})`}
                    </Text>
                    <View
                        style={{
                            flexDirection: "column"
                        }}
                    >
                        {this.state.verdict === "GUILTY" && (
                            <>
                                <View
                                    style={{
                                        height: 15
                                    }}
                                />
                                <Input
                                    onChangeText={t => {
                                        try {
                                            report.fine = parseFloat(t);
                                        } catch (e) {
                                            // console.log(e);
                                        }
                                    }}
                                    keyboardType="decimal-pad"
                                    label="Amount Fined"
                                    defaultValue={report.fine}
                                    containerStyle={{
                                        width: 100,
                                        alignSelf: "center"
                                    }}
                                />
                                <Input
                                    defaultValue={report.points}
                                    containerStyle={{
                                        width: 100,
                                        alignSelf: "center"
                                    }}
                                    onChangeText={t => {
                                        try {
                                            report.points = parseInt(t);
                                        } catch (e) {
                                            // console.log(e);
                                        }
                                    }}
                                    keyboardType="numeric"
                                    label="Points"
                                />
                                <View
                                    style={{
                                        height: 15
                                    }}
                                />
                            </>
                        )}
                        <Button
                            onPress={() => {
                                DocumentPicker.getDocumentAsync({
                                    type: "application/pdf"
                                }).then((response: any) => {
                                    if (response.canceled || response.type === "cancel") {
                                        return;
                                    }
                                    this.uploadAndChangeStatus(
                                        report,
                                        response,
                                        this.state.verdict
                                    );
                                });
                            }}
                            title="Upload"
                        />
                        <Button
                            onPress={() => {
                                Alert.alert(
                                    "TLC Status Pending",
                                    "Please allow some time to receive the verdict from the TLC in the PDF file format."
                                );
                            }}
                            title="I don't have one"
                        />
                        <Button
                            onPress={() => {
                                api
                                    .changeStatus({
                                        id: report.id,
                                        status: this.state.verdict,
                                        fine: report.fine,
                                        points: report.points
                                    })
                                    .then(f => {
                                        const newStatus = statuses.find(
                                            key => key.key === this.state.verdict
                                        );
                                        report.status = newStatus.sId;
                                        this.setState({
                                            selectedRow: undefined,
                                            verdict: undefined
                                        });
                                    })
                                    .catch(e => {
                                        alert("Problem changing status.");
                                    });
                            }}
                            title="I don't want to."
                        />
                        <Button
                            onPress={() => {
                                this.setState({ verdict: undefined, selectedRow: undefined });
                            }}
                            title={"Close"}
                        />
                    </View>
                </View>
            </TypedOverlay>
        );
    }

    get selectedRowOverlay() {
        if (!this.state.selectedRow || this.state.verdict) {
            return <></>;
        }
        const { report, address } = this.state.selectedRow;
        return (
            <TypedOverlay
                isVisible={this.state.selectedRow != undefined && !this.state.verdict}
                width="auto"
                height="auto"
                onBackdropPress={() => this.setState({ selectedRow: undefined })}
            >
                <TypedCard
                    title={`${report.license.plate}\n${address.building} ${address.street} ${address.city}`}
                >
                    <View
                        style={{
                            paddingTop: 10,
                            paddingLeft: 10,
                            paddingRight: 10,
                            flexDirection: "column",
                            justifyContent: "space-around"
                        }}
                    >
                        <Button
                            onPress={() => this.changeStatus(report, "SUMMONS")}
                            title="Summons Issued"
                        />
                        <Button
                            onPress={() => this.changeStatus(report, "HEARING")}
                            title="Hearing Scheduled"
                        />
                        <Button
                            onPress={() => this.guiltyNotGuilty(report, "GUILTY")}
                            title="Driver Paid Fine / Guilty"
                        />
                        <Button
                            onPress={() => this.changeStatus(report, "UNABLE_TO_ID")}
                            title="Unable to ID Driver"
                        />
                        <Button
                            onPress={() => this.guiltyNotGuilty(report, "NOT_GUILTY")}
                            title="Driver Not Guilty"
                        />
                        <Button
                            onPress={() => this.changeStatus(report, "NO_REASON_ARCHIVE")}
                            title="No Reason / Archive"
                        />
                    </View>
                </TypedCard>
            </TypedOverlay>
        );
    }

    _keyExtractor = (item, index) => item.report.id;

    _onRefresh = () => {
        this.setState({ results: [] }, () => {
            this.fetchReports();
        });
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
        this.fetchReports(this.state.reports.length);
    };

    render() {
        return (
            <>
                <View
                    style={{
                        zIndex: 9999
                    }}
                >
                    {!this.state.refreshing && this.state.reports.length === 0 && (
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
                            <Text>No reports found.</Text>
                        </View>
                    )}
                    {this.state.filter && (
                        <View>
                            <FlatList<string>
                                horizontal={true}
                                data={this._filterData}
                                style={{ backgroundColor: "#FFF" }}
                                renderItem={({ item }) => {
                                    return <Button title={item} />;
                                }}
                            />
                            <Button
                                icon={<Icon name="close" size={15} color="white" />}
                                onPress={() => {
                                    this.setState({ filter: undefined }, () => {
                                        this.fetchReports();
                                    });
                                }}
                                title="Clear"
                            />
                        </View>
                    )}
                    <TypedListSpoof
                        style={{
                            width: "100%",
                            height: "100%"
                        }}
                        renderHiddenItem={(data: any) => {
                            const { report, address } = data.item;
                            // console.log("made it", report, address);
                            return (
                                <View style={styles.rowBack}>
                                    {report.status > 0 && (
                                        <Button
                                            onPress={() =>
                                                this.setState({
                                                    selectedRow: {
                                                        report,
                                                        address
                                                    }
                                                })
                                            }
                                            title={"Change Status"}
                                            type="outline"
                                        />
                                    )}
                                    {report.status < 0 && (
                                        <TypedIcon
                                            onPress={() => {
                                                const { report } = data.item;
                                                Alert.alert(
                                                    "Confirm Delete?",
                                                    "Are you sure you want to delete this report?",
                                                    [
                                                        { text: "Cancel", style: "cancel" },
                                                        {
                                                            text: "Ok",
                                                            onPress: () => this.deleteReport(report)
                                                        }
                                                    ]
                                                );
                                            }}
                                            name="delete"
                                        />
                                    )}
                                </View>
                            );
                        }}
                        leftOpenValue={0}
                        rightOpenValue={-160}
                        renderSectionHeader={({ section: { title } }) => {
                            return (
                                <View>
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
                                    {false && (
                                        <TypedIcon
                                            buttonStyle={{
                                                alignSelf: "center",
                                                height: "100%"
                                            }}
                                            containerStyle={{
                                                position: "absolute",
                                                alignSelf: "flex-end",
                                                top: 0,
                                                bottom: 0,
                                                backgroundColor: "blue"
                                            }}
                                            name="swap-vert"
                                        />
                                    )}
                                </View>
                            );
                        }}
                        keyExtractor={this._keyExtractor}
                        sections={this.state.sections}
                        onRefresh={this._onRefresh}
                        refreshing={this.state.refreshing}
                        data={this.state.reports}
                        onEndReached={this._onEndReached}
                        initialNumToRender={2}
                        ListFooterComponent={<View style={{ height: 10 }} />}
                        renderItem={({ item }: { item: ReportListItem }) => {
                            const { report, address } = item;

                            return (
                                <TouchableHighlight
                                    onPress={() => {
                                        this.setState({ selectedRow: { report, address } });
                                    }}
                                    style={{ backgroundColor: "#FFF" }}
                                    key={report.id}
                                >
                                    <ReportView report={item} />
                                </TouchableHighlight>
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
                            {this.state.refreshing && <Text>Loading...</Text>}
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
                                <TypedIcon name="refresh" size={100} />
                                <Text
                                    style={{ textAlign: "center" }}
                                >{`${this.state.error}\n\nTap to retry`}</Text>
                            </TouchableOpacity>
                        </View>
                    )}
                </View>
                {this.submissionsFilter}
                {this.selectedRowOverlay}
                {this.notGuiltyOverlay}
            </>
        );
    }
}

const styles = StyleSheet.create({
    rowBack: {
        alignItems: "center",
        backgroundColor: "#DDD",
        flex: 1,
        flexDirection: "row",
        justifyContent: "flex-end",
        paddingRight: 15
    }
});
