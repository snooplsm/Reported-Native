import React from "react";
import Autocomplete from "react-native-autocomplete-input";
import {
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  Text,
  View
} from "react-native";
import { Button, Icon } from "react-native-elements";
import { AutoStyle } from "./Styles";
import { addresses } from "./Addresses.js";
import { GooglePlacesAutocomplete } from "react-native-google-places-autocomplete";

export default class AddressView extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      listViewDisplayed: true
    };
  }

  render() {
    return (
      <GooglePlacesAutocomplete
        style={{ flex: null }}
        getDefaultValue={() => ""}
        placeholder={this.props.placeholder ?? "Street Address of Complaint"}
        minLength={3} // minimum length of text to search
        autoFocus={true}
        returnKeyType={"search"} // Can be left out for default return key https://facebook.github.io/react-native/docs/textinput.html#returnkeytype
        keyboardAppearance={"light"} // Can be left out for default keyboardAppearance https://facebook.github.io/react-native/docs/textinput.html#keyboardappearance
        listViewDisplayed={this.state.listViewDisplayed} // true/false/undefined
        fetchDetails={true}
        renderDescription={row => {
          console.log(row);
          return row.description;
        }} // custom description render
        onPress={(data, details = null) => {
          // 'details' is provided when fetchDetails = true
          this.props.onPress({
            data: data,
            place: details
          });
          //this.setState({ listViewDisplayed: false });
        }}
        getDefaultValue={() => ""}
        query={{
          // available options: https://developers.google.com/places/web-service/autocomplete
          key: "AIzaSyDiBYFqZLwPsNkMbRNqr1_63h-w9fcZNVM",
          language: "en" // language of the results
        }}
        styles={{
          textInputContainer: {
            width: "100%"
          },
          description: {
            fontWeight: "bold"
          },
          predefinedPlacesDescription: {
            color: "#1faadb"
          }
        }}
        currentLocation={false} // Will add a 'Current location' button at the top of the predefined places list
        currentLocationLabel="Current location"
        nearbyPlacesAPI="GooglePlacesSearch" // Which API to use: GoogleReverseGeocoding or GooglePlacesSearch
        GoogleReverseGeocodingQuery={
          {
            // available options for GoogleReverseGeocoding API : https://developers.google.com/maps/documentation/geocoding/intro
          }
        }
        GooglePlacesSearchQuery={{
          // available options for GooglePlacesSearch API : https://developers.google.com/places/web-service/search
          rankby: "distance"
        }}
        enablePoweredByContainer={false}
        GooglePlacesDetailsQuery={{
          // available options for GooglePlacesDetails API : https://developers.google.com/places/web-service/details
          fields: "formatted_address"
        }}
        filterReverseGeocodingByTypes={[
          "locality",
          "administrative_area_level_3"
        ]} // filter the reverse geocoding results by types - ['locality', 'administrative_area_level_3'] if you want to display only cities
        debounce={200} // debounce the requests in ms. Set to 0 to remove debounce. By default 0ms.
      />
    );
  }
}

const styles = StyleSheet.create({
  text: {},
  renderItem: {
    paddingTop: 8,
    paddingBottom: 8,
    paddingLeft: 10,
    paddingRight: 10,
    margin: 3
  },
  buttonContainer: {
    padding: 1,
    margin: 1
  },
  button: {
    backgroundColor: "#ec682c",
    borderRadius: 2,
    borderWidth: 1
  },
  buttonText: {
    fontSize: 14
  }
});
