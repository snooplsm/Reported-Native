import React from "react";
import Autocomplete from "react-native-autocomplete-input";
import {
  StyleSheet,
  Text,
  View,
  Keyboard,
  TouchableOpacity
} from "react-native";
import TouchSpoof from "./TouchSpoof";
import { Button, Icon } from "react-native-elements";
import { categories } from "./Categories.js";

export default class ComplaintView extends React.Component {
  constructor(props) {
    super(props);
    this.auto = React.createRef();
    const complaints = [...categories];
    this.state = {
      query: "",
      complaintTypes: complaints,
      complaints: props.complaints ?? [],
      hideResults: false
    };
  }

  _filterData(query) {
    const q = query.toLowerCase();
    const complaints = new Set(this.state.complaints);
    return this.state.complaintTypes
      .filter(x => !complaints.has(x))
      .filter(x => {
        const lower = x.name.toLowerCase();
        return lower.includes(q);
      });
  }

  componentDidMount() {
    Keyboard.dismiss();
  }

  render() {
    const { query } = this.state;
    const data = this._filterData(query);
    return (
      <>
        <View style={styles.container}>
          {this.state.complaints.map(complaint => {
            return (
              <Button
                key={complaint.id}
                containerStyle={styles.buttonContainer}
                buttonStyle={styles.button}
                title={complaint.name}
                titleStyle={styles.buttonText}
                onPress={() => {
                  const state = Object.assign({}, this.state);
                  const complaints = [...this.state.complaints].filter(
                    x => x !== complaint
                  );
                  state.complaints = complaints;
                  let onComplaintsChanged =
                    this.props.onComplaintsChanged ?? (() => {});
                  this.setState(state);
                  onComplaintsChanged(complaints);
                }}
                /*icon={
                <Icon
                  color={'white'}
                  type='material'
                  name='close'/>
              }
              iconRight={true}*/
              />
            );
          })}
        </View>
        <TouchSpoof onPress={() => {}}>
          <View
            style={[
              {
                padding: 0
              },
              styles.autocompleteContainer
            ]}
          >
            <Autocomplete
              data={data}
              ref={this.auto}
              defaultValue={this.state.query}
              editable={false}
              autoFocus={false}
              hideResults={this.state.hideResults}
              placeholder={"Complaint Type, Blocked Bike lane, Crosswalk"}
              onFocus={() => {
                const state = Object.assign({}, this.state);
                state.hideResults = false;
                this.setState(state);
              }}
              onChangeText={text => {
                const state = Object.assign({}, this.state);
                state.query = text;
                state.hideResults = false;
                this.setState(state);
              }}
              onBlur={() => {
                const state = Object.assign({}, this.state);
                state.hideResults = true;
                this.setState(state);
              }}
              renderItem={({ item, i }) => (
                <TouchableOpacity
                  key={item.id}
                  style={styles.renderItem}
                  onPress={() => {
                    const state = Object.assign({}, this.state);
                    state.hideResults = true;
                    state.query = "";
                    const complaints = [...state.complaints, item];
                    state.complaints = complaints.filter(
                      (x, index) => complaints.indexOf(x) == index
                    );
                    this.setState(state);
                    this.auto.current.blur();
                    let onComplaintsChanged =
                      this.props.onComplaintsChanged ?? (() => {});
                    onComplaintsChanged(complaints);
                  }}
                >
                  <Text style={styles.text}>{item.name}</Text>
                </TouchableOpacity>
              )}
            />
          </View>
        </TouchSpoof>
      </>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flexWrap: "wrap",
    flexDirection: "row"
  },
  autocompleteContainer: {
    flex: 1,
    left: 0,
    position: "absolute",
    right: 0,
    top: 0,
    zIndex: 1
  },
  text: {},
  renderItem: {
    paddingTop: 8,
    paddingBottom: 8,
    paddingLeft: 10,
    paddingRight: 10,
    margin: 3,
    backgroundColor: "#ffffff"
  },
  buttonContainer: {
    padding: 1,
    margin: 1
  },
  button: {
    borderRadius: 2,
    borderWidth: 1
  },
  buttonText: {
    fontSize: 14
  }
});
