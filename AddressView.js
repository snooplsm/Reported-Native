import React from "react";
import Autocomplete from "react-native-autocomplete-input";
import {
  Image,
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  Text,
  View,
  SafeAreaView
} from "react-native";
import MapView, { Marker } from "react-native-maps";
import { Button, Icon, Input } from "react-native-elements";
import { AutoStyle } from "./Styles";
import { addresses } from "./Addresses.js";
import marker from "./assets/car-marker.png";
import { GooglePlacesAutocomplete } from "react-native-google-places-autocomplete";

export default class AddressView extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      listViewDisplayed: true,
      region: {
        latitude: 40.70696,
        longitude: -73.973621,
        latitudeDelta: 0.0922,
        longitudeDelta: 0.0421
      },
      camera: {
        latitude: 40.70696,
        longitude: -73.973621
      }
    };
  }

  onRegionChange = region => {
    this.setState({
      region
    });
  };

  render() {
    const { region } = this.state;
    return (
      <>
        {!this.state.map && (
          <GooglePlacesAutocomplete
            style={{ flex: null }}
            getDefaultValue={() => ""}
            placeholder={
              this.props.placeholder ?? "Street Address of Complaint"
            }
            minLength={3} // minimum length of text to search
            autoFocus={true}
            returnKeyType={"search"} // Can be left out for default return key https://facebook.github.io/react-native/docs/textinput.html#returnkeytype
            keyboardAppearance={"light"} // Can be left out for default keyboardAppearance https://facebook.github.io/react-native/docs/textinput.html#keyboardappearance
            listViewDisplayed={this.state.listViewDisplayed} // true/false/undefined
            fetchDetails={true}
            renderDescription={row => {
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
            renderRightButton={() => (
              <Icon
                onPress={() => {
                  this.setState({ map: true });
                }}
                name="map"
                size={26}
              />
            )}
            styles={{
              textInputContainer: {
                backgroundColor: "rgba(0,0,0,0)",
                borderTopWidth: 0,
                borderBottomWidth: 0
              },
              textInput: {
                marginLeft: 10,
                marginRight: 10,
                height: 38,
                color: "#5d5d5d",
                fontSize: 16,
                borderRadius: 2,
                borderWidth: 1,
                borderColor: "black"
              },
              predefinedPlacesDescription: {
                color: "#1faadb"
              }
            }}
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
        )}
        {this.state.map && (
          <View style={styles.map}>
            <MapView
              style={styles.map}
              // initialCamera={this.state.camera}
              zoomEnabled={true}
              initialRegion={region}
              onRegionChangeComplete={this.onRegionChange}
              style={{ flex: 1 }}
            >
              <View style={styles.markerFixed}>
                <Image style={styles.marker} source={marker} />
              </View>
            </MapView>
          </View>
        )}
      </>
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
  },
  map: {
    flex: 1
  },
  markerFixed: {
    left: "50%",
    marginLeft: -24,
    marginTop: -48,
    position: "absolute",
    top: "50%"
  },
  marker: {
    height: 48,
    width: 48
  },
  footer: {
    backgroundColor: "rgba(0, 0, 0, 0.5)",
    bottom: 0,
    position: "absolute",
    width: "100%"
  },
  region: {
    color: "#fff",
    lineHeight: 20,
    margin: 20
  }
});
