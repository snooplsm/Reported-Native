import React from "react";
// import Autocomplete from "react-native-autocomplete-input";
import {
  StyleSheet,
  Text,
  View,
  FlatList,
  Keyboard,
  TouchableOpacity
} from "react-native";
// import TouchSpoof from "./TouchSpoof";
// import { Button, Icon } from "react-native-elements";
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

  _renderItem = ({ item: complaint }) => {
    return (
      <TouchableOpacity
        key={complaint.id}
        style={styles.renderItem}
        onPress={() => {
          const complaints = [...this.state.complaints, complaint];
          let onComplaintsChanged =
            this.props.onComplaintsChanged ?? (() => { });
          onComplaintsChanged(complaints);
        }}
      >
        <Text key={complaint.name} style={styles.text}>
          {complaint.name}
        </Text>
      </TouchableOpacity>
    );
  };

  render() {
    const { query } = this.state;
    const data = this._filterData(query);
    return (
      <FlatList
        keyExtractor={(item, index) => `${index}`}
        style={styles.container}
        data={this.state.complaintTypes}
        renderItem={this._renderItem}
      />
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
