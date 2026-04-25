import React from "react";
// import Autocomplete from "react-native-autocomplete-input";
import {
  Image,
  StyleSheet,
  TouchableOpacity,
  Text,
  View,
  Keyboard,
  FlatList,
  Platform
} from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import MapView, { Polygon } from "react-native-maps";
import { Button, Icon, Input, Overlay } from "react-native-elements";
import Autolink from "react-native-autolink";
import { findInLocation } from "./utils/locations";
import marker from "../assets/car-marker.png";
import { geocode, reverseGeocode, precincts } from "./Api";
import { env } from "./env";
const polylineUtil = require("@mapbox/polyline");
import { isPointInPolygon } from "geolib";
import { GooglePlacesAutocomplete } from "react-native-google-places-autocomplete";

const TypedOverlay = Overlay as React.ComponentType<any>;
const TypedGooglePlacesAutocomplete = GooglePlacesAutocomplete as React.ComponentType<any>;

type AddressViewProps = {
  location?: any;
  onPress?: (payload: { data?: unknown; place: any }) => void;
  onFocus?: () => void;
  placeholder?: string;
};

export default class AddressView extends React.Component<AddressViewProps, any> {
  key: string;
  private _precincts: any[];
  private debounce?: ReturnType<typeof setTimeout>;

  constructor(props: AddressViewProps) {
    super(props);
    this.key = `address.view.state`;
    this._precincts = [];
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
      },
      precincts: []
    };
  }

  precinctsInBounds = async () => {
    const bounds = this.state.region;
    const n = { latitude: bounds.latitude + bounds.latitudeDelta };
    const s = { latitude: bounds.latitude - bounds.latitudeDelta };
    const e = { longitude: bounds.longitude + bounds.longitudeDelta };
    const w = { longitude: bounds.longitude - bounds.longitudeDelta };
    const ne = {
      ...n,
      ...e
    };
    const nw = {
      ...n,
      ...w
    };
    const sw = {
      ...s,
      ...w
    };
    const se = {
      ...s,
      ...e
    };

    const big = [ne, se, sw, nw];
    const precincts = (this._precincts || []).sort(() => 0);
    // // console.log("big", big);
    //this.setState({ big });

    // // console.log(big);
    const some = precincts.filter(precinct => {
      //// console.log(precinct.id);
      const some = precinct.polygons.some(polygon => {
        return polygon.some(point => {
          // // console.log(point);
          return isPointInPolygon(point, big);
        });
      });
      return some;
    });
    // // console.log("found", some.length);
    // // console.log("precinctsinbounds end", Date());
    return some;
  };

  // precinctWithin = async location => {
  //   const start = Date();
  //   // console.log("precinctswithinstart", start);
  //   const precincts = this._precincts || [];
  //   const point = { latitude: location.lat, longitude: location.lng };
  //   const result = precincts.find(precinct => {
  //     return precinct.polygons.some(polygon => {
  //       const isInPoly = isPointInPolygon(point, polygon);
  //       //// console.log(isInPoly);
  //       if (isInPoly) {
  //         // console.log("found", precinct.id);
  //       }
  //       return isInPoly;
  //     });
  //   });
  //   return result;
  // };

  doBgShit() {
    // // console.log("dobgshit");
    const { region } = this.state;
    if (!region) {
      return;
    }
    const location = { lat: region.latitude, lng: region.longitude };
    // const start = new Date().valueOf();
    //const precinct = await this.precinctWithin(location);
    // const end = new Date().valueOf();
    // const start2 = new Date().valueOf();
    // const precincts = await this.precinctsInBounds();
    // const end2 = new Date().valueOf();
    //
    // // console.log("milliseconds ellapsed", end - start, end2 - start);
    // // console.log("precinct", precinct.name, precinct.id, precinct.social);
    this.setState({ precinct: undefined });

    clearTimeout(this.debounce);
    this.debounce = setTimeout(() => {
      // // console.log("after timeout", Date());
      const { region } = this.state;
      if (!region) {
        return;
      }
      // // console.log("debounce");
      reverseGeocode(location)
        .then((data: any) => {
          //// console.log(data);
          // // console.log("reverse geocoded");
          const { results: pre } = data;
          if (pre) {
            const formattedAddress: Record<string, unknown> = {};
            const results: any[] = [];
            pre.forEach((x: any) => {
              const { address_components: address } = x;
              const premise = findInLocation(address, "premise");
              const building = findInLocation(address, "street_number");
              const street = findInLocation(address, "route");
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
          // console.log(e);
        });
    }, 0);
  }

  onRegionChange = async (region: any) => {
    this.setState(
      {
        region
      },
      () => {
        this.doBgShit();
      }
    );
  };

  componentWillUnount() {
    clearTimeout(this.debounce);
  }

  _keyExtractor = (item, index) => item.formatted_address;

  _renderItem = ({ item }) => {
    const { address_components: address } = item;
    const premise = findInLocation(address, "premise");
    const building = findInLocation(address, "street_number");
    const street = findInLocation(address, "route");
    let title = "";
    if (premise) {
      title = [premise, street].filter(x => x).join(" ");
    } else {
      title = [building, street].join(" ");
    }
    return (
      <Button
        key={item.id}
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
    precincts()
      .then((precincts: any[]) => {
        if (!precincts) {
          return;
        }
        this._precincts = precincts
          .sort((a, b) => {
            return b.polylines.length - a.polylines.length;
          })
          .map(precinct => {
            precinct.polygons = precinct.polylines.map((polyline: string) =>
              polylineUtil.decode(polyline).map((arr: number[]) => {
                return {
                  latitude: arr[0],
                  longitude: arr[1]
                };
              })
            );
            delete precinct.polylines;
            return precinct;
          });
      })
      .catch(e => {
        // console.log("precincts error", e);
      });
  }

  saveOffline = async () => {
    AsyncStorage.setItem(this.key, JSON.stringify(this.state));
  };

  loadOffline = async () => {
    // console.log("load");
    AsyncStorage.getItem(this.key)
      .then(state => {
        // console.log("got state");
        if (state) {
          const state2 = JSON.parse(state);
          super.setState(state2);
        }
      })
      .catch(e => {
        // console.log(e);
      });
  };

  setState(data: any, lambda?: () => void) {
    super.setState(data, () => {
      this.saveOffline();
      if (lambda) {
        lambda();
      }
    });
  }

  get selectedPrecinct() {
    const selectedPrecinct = this.state.selectedPrecinct;
    if (!selectedPrecinct) {
      return <></>;
    }
    // console.log(selectedPrecinct.social.twitter);
    return (
      <TypedOverlay
        isVisible={true}
        width="auto"
        height="auto"
        onBackdropPress={() => {
          this.setState({ selectedPrecinct: undefined });
        }}
        overlayBackgroundColor="#ffffffCC"
        windowBackgroundColor={null}
      >
        <View style={{ padding: 20 }}>
          <Text
            style={{ alignSelf: "center" }}
          >{`${selectedPrecinct.name}`}</Text>
          <Text style={{ alignSelf: "center" }}>
            {selectedPrecinct.social && selectedPrecinct.social.address}
          </Text>
          {selectedPrecinct.social && selectedPrecinct.social.phone && (
            <Autolink
              style={{ alignSelf: "center" }}
              phone={true}
              text={`${selectedPrecinct.social.phone}`}
            />
          )}
          {selectedPrecinct.social && selectedPrecinct.social.twitter && (
            <Autolink
              style={{ alignSelf: "center" }}
              text={`https://twitter.com/${selectedPrecinct.social.twitter}`}
            />
          )}
        </View>
      </TypedOverlay>
    );
  }

  render() {
    const { region } = this.state;
    return (
      <>
        {!this.state.map && (
          <TypedGooglePlacesAutocomplete
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
              // // console.log("place", details);
              // // console.log("data", data);
              geocode(data.description).then(ok => {
                this.props.onPress({
                  data: data,
                  place: ok
                });
              });
            }}
            query={{
              // available options: https://developers.google.com/places/web-service/autocomplete
              key: env.googleMapsApiKey,
              language: "en" // language of the results
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
                width: "100%",
                backgroundColor: "rgba(0,0,0,0)",
                borderTopWidth: 0,
                borderBottomWidth: 0
              },
              description: {
                fontWeight: "bold"
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
              style={[
                Platform.OS === "ios" ? styles.mapIos : styles.mapAndroid,
                { flex: 1 }
              ]}
              zoomEnabled={true}
              initialRegion={region}
              onRegionChangeComplete={this.onRegionChange}
            >
              {this._precincts.map(precinct => {
                return precinct.polygons.map(polygon => {
                  return (
                    <Polygon
                      tappable
                      onPress={() => {
                        this.setState({ selectedPrecinct: precinct });
                      }}
                      coordinates={polygon}
                    />
                  );
                });
              })}
              {this.state.big && (
                <Polygon
                  coordinates={this.state.big}
                  strokeWidth={5}
                  strokeColor={"red"}
                />
              )}
            </MapView>
            {true && (
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
                <View pointerEvents="none" style={styles.markerFixed}>
                  <Image style={styles.marker} source={marker} />
                </View>
              </>
            )}

            <View style={styles.footer}>
              {this.state.precinct && (
                <Text
                  onPress={() => {
                    this.setState({ selectedPrecinct: this.state.precinct });
                  }}
                  style={{
                    padding: 10,
                    alignSelf: "center",
                    backgroundColor: "#000000",
                    borderRadius: 5,
                    color: "#FFF"
                  }}
                >
                  {this.state.precinct.name}
                </Text>
              )}
              <FlatList
                keyExtractor={this._keyExtractor}
                horizontal={true}
                renderItem={this._renderItem}
                data={this.state.results}
              />
            </View>
            {this.selectedPrecinct}
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
    width: "100%",
    bottom: 0,
    position: "absolute"
  },
  region: {
    color: "#fff",
    lineHeight: 20,
    margin: 20
  }
});
