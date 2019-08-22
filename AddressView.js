import React from "react";
import Autocomplete from "react-native-autocomplete-input";
import {
  AsyncStorage,
  Image,
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  Text,
  View,
  SafeAreaView,
  Keyboard,
  FlatList,
  Platform
} from "react-native";
import MapView, { Marker } from "react-native-maps";
import { Button, Icon, Input } from "react-native-elements";
import { AutoStyle } from "./Styles";
import { addresses } from "./Addresses.js";
import marker from "./assets/car-marker.png";
import { geocode, reverseGeocode, finds } from "./Api";
import { GooglePlacesAutocomplete } from "react-native-google-places-autocomplete";

export default class AddressView extends React.Component {
  constructor(props) {
    super(props);
    this.key = `address.view.state`;
    const location = (props.location &&
      props.location.place.geometry.location) || {
      lat: 40.70696,
      lng: -73.973621
    };

    this.state = {
      listViewDisplayed: true,
      region: {
        latitude: location.lat,
        longitude: location.lng,
        latitudeDelta: 0.0922,
        longitudeDelta: 0.0421
      },
      camera: {
        latitude: location.lat,
        longitude: location.lng,
        zoom: 10.0
      }
    };
  }

  onRegionChange = region => {
    this.setState(
      {
        region
      },
      () => {
        this.debounce = setTimeout(() => {
          const { region } = this.state;
          if (!region) {
            return;
          }
          const location = { lat: region.latitude, lng: region.longitude };

          reverseGeocode(location)
            .then(data => {
              //console.log(data);
              const { results: pre } = data;
              if (pre) {
                const formattedAddress = {};
                const results = [];
                pre.forEach((x, index) => {
                  const { address_components: address } = x;
                  const premise = finds(address, "premise");
                  const building = finds(address, "street_number");
                  const street = finds(address, "route");
                  if (!premise && !building && !street) {
                    //alert("no dice", premise, building, street);
                  } else {
                    //alert("we good");
                    if (!formattedAddress[x.formatted_address]) {
                      formattedAddress[x.formatted_address] = x;
                      results.push(x);
                    }
                  }
                });
                this.setState({ results });
              }
            })
            .catch(e => {
              console.log(e);
            });
        }, 400);
      }
    );
  };

  componentWillUnount() {
    clearTimeout(this.debounce);
  }

  _keyExtractor = (item, index) => item.formatted_address;

  _renderItem = ({ item }) => {
    const { address_components: address } = item;
    const premise = finds(address, "premise");
    const building = finds(address, "street_number");
    const street = finds(address, "route");
    let title = "";
    if (premise) {
      title = [premise, street].filter(x => x).join(" ");
    } else {
      title = [building, street].join(" ");
    }
    return (
      <Button
        id={item.id}
        title={title}
        onPress={() => {
          this.props.onPress({
            place: item
          });
        }}
        containerStyle={{
          padding: 5,
          opacity: 0.75
        }}
        buttonStyle={{
          backgroundColor: "#FFF",
          padding: 10
        }}
        titleStyle={{
          color: "black"
        }}
      />
    );
  };

  componentDidMount() {
    this.loadOffline();
  }

  saveOffline = async () => {
    console.log("save");
    AsyncStorage.setItem(this.key, JSON.stringify(this.state));
  };

  loadOffline = async () => {
    console.log("load");
    AsyncStorage.getItem(this.key)
      .then(state => {
        console.log("got state");
        if (state) {
          const state2 = JSON.parse(state);
          super.setState(state2);
        }
      })
      .catch(e => {
        console.log(e);
      });
  };

  setState(data, lambda) {
    super.setState(data, () => {
      this.saveOffline();
      if (lambda) {
        lambda();
      }
    });
  }

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
            fetchDetails={false}
            renderDescription={row => {
              return row.description;
            }} // custom description render
            onPress={(data, details = null) => {
              // 'details' is provided when fetchDetails = true
              console.log("place", details);
              console.log("data", data);
              geocode(data.description).then(ok => {
                this.props.onPress({
                  data: data,
                  place: ok
                });
              });
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
              <View style={{ marginTop: 10, marginRight: 10 }}>
                <TouchableOpacity>
                  <Icon
                    onPress={() => {
                      Keyboard.dismiss();
                      this.setState({ map: true });
                    }}
                    name="map"
                    containerStyle={
                      {
                        //padding: 2
                      }
                    }
                    size={30}
                  />
                </TouchableOpacity>
              </View>
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
          <View
            style={
              Platform.OS === "ios"
                ? styles.mapIosContainer
                : styles.mapAndroidContainer
            }
          >
            <MapView
              style={Platform.OS === "ios" ? styles.mapIos : styles.mapAndroid}
              // initialCamera={this.state.camera}
              zoomEnabled={true}
              initialRegion={region}
              onRegionChangeComplete={this.onRegionChange}
              style={{ flex: 1 }}
            >
              {Platform.OS === "ios" && (
                <>
                  <View style={{ right: 0, position: "absolute" }}>
                    <Icon
                      onPress={() => {
                        this.setState({ map: undefined });
                      }}
                      name="list"
                      size={40}
                    />
                  </View>
                  <View style={styles.markerFixed}>
                    <Image style={styles.marker} source={marker} />
                  </View>
                </>
              )}
            </MapView>
            {Platform.OS === "android" && (
              <>
                <View style={{ right: 0, position: "absolute" }}>
                  <Icon
                    onPress={() => {
                      this.setState({ map: undefined });
                    }}
                    name="list"
                    size={40}
                  />
                </View>
                <View style={styles.markerFixed}>
                  <Image style={styles.marker} source={marker} />
                </View>
              </>
            )}

            <FlatList
              style={styles.footer}
              horizontal={true}
              renderItem={this._renderItem}
              data={this.state.results}
            />
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
  mapIosContainer: {
    width: "100%",
    height: "100%"
  },
  mapIos: {
    flex: 1
  },
  mapAndroid: {},
  mapAndroidContainer: {
    flex: 1
  },
  markerFixed: {
    left: "50%",
    marginLeft: -40,
    marginTop: -100,
    position: "absolute",
    top: "50%"
  },
  marker: {
    height: 100,
    width: 80
  },
  footer: {
    bottom: 0,
    position: "absolute"
  },
  region: {
    color: "#fff",
    lineHeight: 20,
    margin: 20
  }
});
