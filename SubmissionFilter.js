import React from "react";

import {
  View,
  Text,
  SafeAreaView,
  StyleSheet,
  Modal,
  TouchableOpacity
} from "react-native";
import { Input, Icon, Button } from "react-native-elements";
import AddressView from "./AddressView";
import ComplaintView from "./ComplaintView";
import CalendarView from "./CalendarView";

export default class SubmissionFilter extends React.Component {
  constructor(props) {
    super(props);
    const { when, near, complaints, keywords } = props.filter ?? {};
    console.log(when, near, complaints, keywords);
    this.state = {
      showWhen: false,
      addressStyle: styles.addressStyleBlur,
      when: when,
      near: near,
      complaints: complaints ?? [],
      keywords: keywords
    };
  }

  filterPressed() {
    const onFilterPressed = this.props.onFilterPressed ?? (() => {});
    const { keywords, address: near, complaints, when } = this.state;
    const query = {
      keywords,
      near,
      when,
      complaints
    };
    onFilterPressed(query);
  }

  _when() {
    const { when } = this.state;
    console.log(when, "when");
    if (when) {
      console.log("all possible");
      return when.allPossibleDates;
    } else {
      return "When";
    }
  }

  get complaintModal() {
    if (this.state.showComplaintModal) {
      console.log("show complaint");
      return (
        <View
          style={{
            position: "absolute",
            bottom: 0,
            top: 0,
            left: 0,
            right: 0,
            zIndex: 9999,
            backgroundColor: "white"
          }}
        >
          <ComplaintView
            complaints={this.state.complaints}
            onComplaintsChanged={complaints => {
              this.setState({ complaints, showComplaintModal: false });
            }}
          />
        </View>
      );
    } else {
      return <></>;
    }
  }

  get addressModal() {
    if (this.state.showAddressModal) {
      return (
        <View
          style={{
            position: "absolute",
            bottom: 0,
            top: 0,
            left: 0,
            right: 0,
            zIndex: 9999,
            backgroundColor: "white"
          }}
        >
          <AddressView
            onPress={address => {
              this._address.blur();
              this.setState({ address, showAddressModal: false });
            }}
            onFocus={() =>
              this.setState({ addressStyle: styles.addressStyleNotBlur })
            }
            placeholder={"Near Address"}
          />
        </View>
      );
    }
  }

  render() {
    return (
      <>
        <View style={styles.container}>
          <View style={styles.HorizontalStyle}>
            <Icon
              containerStyle={styles.close}
              onPress={() => this.props.onCloseClicked()}
              name="close"
            />
          </View>
          <Input
            placeholder={""}
            label="Keywords"
            value={this.state.keywords}
            onChangeText={keywords => {
              this.setState({ keywords });
            }}
          />
          <View>
            <TouchableOpacity
              onPress={() => this.setState({ showComplaintModal: true })}
            >
              <Input
                ref={r => (this._complaint = r)}
                caretHidden={true}
                autoFocus={false}
                onFocus={x => this.setState({ showComplaintModal: true })}
                label={"Complaint"}
                placeholder={"Complaint Type, Blocked Bike lane, Crosswalk"}
                value={this.state.complaints.map(x => x.name).join(", ")}
              />
            </TouchableOpacity>
          </View>
          <TouchableOpacity
            onPress={() => {
              this.setState({ showWhen: true });
            }}
          >
            <Input
              pointerEvents="none"
              value={this.state.whenText}
              editable={false}
              placeholder={this._when()}
            />
          </TouchableOpacity>
          <View>
            <TouchableOpacity
              onPress={() => this.setState({ showAddressModal: true })}
            >
              <Input
                ref={r => (this._address = r)}
                caretHidden={true}
                autoFocus={false}
                onFocus={x => this.setState({ showAddressModal: true })}
                label={"Address"}
                placeholder={"Where you observed infraction"}
                value={this.addressString}
              />
            </TouchableOpacity>
          </View>

          <Modal
            visible={this.state.showWhen}
            style={[
              {
                backgroundColor: "red"
              }
            ]}
          >
            <CalendarView
              onClose={() => {
                this.setState({ showWhen: false });
              }}
              onValidDate={valid => {
                this.setState({ showWhen: false, when: valid });
              }}
              onInValidDate={invalid => {
                console.log(invalid.allPossibleDates);
                const when = invalid.allPossibleDates == null ? null : invalid;
                this.setState({ showWhen: false, when: when });
              }}
            />
          </Modal>
        </View>
        <Button
          buttonStyle={{
            borderRadius: 0,
            padding: 20
          }}
          containerStyle={{
            position: "absolute",
            bottom: 0,
            right: 0,
            width: "100%"
          }}
          onPress={() => this.filterPressed()}
          title="Filter"
        />
        {this.complaintModal}
        {this.addressModal}
      </>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    width: "100%",
    height: "100%"
  },
  bottom: {
    width: "100%",
    justifyContent: "flex-end",
    alignSelf: "flex-end",
    bottom: 0
  },
  horiz: {
    width: "100%",
    flexDirection: "row"
  },
  close: {
    alignSelf: "flex-end",
    right: 0,
    padding: 15,
    zIndex: 2
  },
  addressStyleBlur: {
    height: 50
  },

  addressStyleNotBlur: {},
  button: {
    minHeight: 80
  },
  elementModal: {
    position: "absolute",
    top: 0,
    bottom: 0,
    left: 0,
    right: 0
  },
  highOrderElement: {
    zIndex: 2
  }
});
