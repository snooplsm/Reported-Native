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
      complaints: complaints,
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

  render() {
    return (
      <View style={styles.container}>
        <View style={styles.HorizontalStyle}>
          <Icon
            containerStyle={styles.close}
            onPress={() => this.props.onCloseClicked()}
            name="close"
          />
        </View>
        <Input
          placeholder={"Keywords"}
          value={this.state.keywords}
          onChangeText={keywords => {
            this.setState({ keywords });
          }}
        />
        {
          <ComplaintView
            style={styles.element}
            complaints={this.state.complaints}
            onComplaintsChanged={complaints => {
              this.setState({ complaints });
            }}
          />
        }
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
        {
          <Button
            buttonStyle={styles.button}
            containerStyle={[styles.button]}
            onPress={() => this.filterPressed()}
            title="Filter"
          />
        }
        {
          <AddressView
            onPress={address => {
              this.setState({ address });
            }}
            onFocus={() =>
              this.setState({ addressStyle: styles.addressStyleNotBlur })
            }
            onBlur={() =>
              this.setState({ addressStyle: styles.addressStyleBlur })
            }
            placeholder={"Near Address"}
          />
        }

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
  element: {
    //minHeight: 50
  },
  highOrderElement: {
    zIndex: 2
  }
});
