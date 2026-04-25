import React from "react";

import {
  Alert,
  Clipboard,
  Image,
  ImageBackground,
  StyleSheet,
  Linking,
  Text,
  View,
  TouchableOpacity,
  Platform
} from "react-native";
import MapView, { Marker } from "react-native-maps";
import { ToastAndroid } from "react-native";
import { Avatar, Card, Icon, Tooltip } from "react-native-elements";
import Autolink from "react-native-autolink";
import { HorizontalStyle, ButtonStyle } from "./Styles";
// import ImageCarousel from "./ImageCarousel";
import moment from "moment";
import { statusesMap } from "./Statuses";

type Thumbnail = {
  width: number;
  url: string;
};

const TypedAvatar = Avatar as React.ComponentType<any>;
const TypedCard = Card as React.ComponentType<any>;
const TypedTooltip = Tooltip as React.ComponentType<any>;

const reduce512 = (prev: Thumbnail | null, curr: Thumbnail) => {
  if (prev === null) {
    return null;
  }
  return Math.abs(curr.width - 512) < Math.abs(prev.width - 512) ? curr : prev;
};

export default class ReportView extends React.Component<any, any> {
  constructor(props: any) {
    super(props);
  }

  carousel() {
    const { report: rpt } = this.props;
    const { report, address } = rpt;
    if (report.media && report.media.length > 0) {
      return report.media.map((image, index) => {
        const thumb = !!image.thumbnails && !!image.thumbnails.length && image.thumbnails.reduce(reduce512);
        if (!thumb) {
          return null;
        }
        return (
          <View key={index}>
            <TouchableOpacity onPress={() => Linking.openURL(image.url)}>
              <ImageBackground
                source={{ uri: thumb.url }}
                resizeMode="cover"
                style={{
                  width: "100%",
                  height: 256
                }}
              />
              {image.type === "YOUTUBE" && (
                <View
                  style={{
                    position: "absolute",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    justifyContent: "center",
                    alignItems: "center"
                  }}
                >
                  <View
                    style={{
                      position: "absolute",
                      width: 80,
                      height: 80,
                      borderRadius: 80 / 2,
                      backgroundColor: "#FFFFFF99"
                    }}
                  />
                  <Icon name="play-circle-outline" type="material" size={100} />
                </View>
              )}
            </TouchableOpacity>

            {index !== report.media.length - 1 && (
              <View style={{ height: 10, paddingTop: 10 }}></View>
            )}
          </View>
        );
      });
    } else {
      return null;
    }
  }

  get status() {
    const { report: rpt } = this.props;
    const { report, address } = rpt;
    if (report.status <= 0) {
      return "PENDING";
    } else {
      return !!statusesMap[report.status] && statusesMap[report.status].text;
    }
  }

  copy(value: string) {
    Clipboard.setString(value);
    if (Platform.OS === "ios") {
      Alert.alert("Copied to clipboard", value);
    } else if (Platform.OS === "android") {
      ToastAndroid.showWithGravity(
        `Copied to clipboard\n${value}`,
        ToastAndroid.LONG,
        ToastAndroid.BOTTOM
      );
    }
  }

  render() {
    const { report: rpt } = this.props;
    const { report, address } = rpt;
    const time = moment(report.timeofincident);
    // console.log(report.fine, report.points);
    return (
      <View key={rpt.id}>
        <TypedCard title={report.complaint}>
          <View
            style={[
              HorizontalStyle.style,
              {
                paddingBottom: 10
              }
            ]}
          >
            {
              address.location ?
                <TypedTooltip
                  popover={
                    <MapView
                      style={{
                        width: 200,
                        height: 200
                      }}
                      initialRegion={{
                        latitude: address.location.lat,
                        longitude: address.location.lng,
                        latitudeDelta: 0.01,
                        longitudeDelta: 0.01
                      }}
                    >
                      <Marker
                        coordinate={{
                          latitude: address.location.lat,
                          longitude: address.location.lng
                        }}
                      />
                    </MapView>
                  }
                >
                  <Text>
                    {address.building} {address.street} {address.city}
                  </Text>
                </TypedTooltip> :
                <View />
            }
            <TypedTooltip
              popover={
                <Text style={{ color: "white", fontWeight: "bold" }}>
                  License plate {report.license.plate}
                </Text>
              }
              backgroundColor={"black"}
            >
              <Text
                style={[
                  ButtonStyle.orangeText,
                  ,
                  {
                    borderWidth: 1,
                    borderColor: "black",
                    paddingTop: 2,
                    paddingBottom: 2,
                    paddingLeft: 4,
                    paddingRight: 4
                  }
                ]}
              >
                {report.license.plate}
              </Text>
            </TypedTooltip>
          </View>
          {this.carousel()}

          <Autolink text={report.description ?? ""} />
          <Autolink text={report.notes ?? ""} />
          {(report.searchId === null || report.searchId.length === 0) && (
            <Autolink
              style={ButtonStyle.orangeText}
              text={`311# Pending Submission`}
            />
          )}
          {report.searchId != null && report.searchId.length > 0 && (
            <TouchableOpacity onLongPress={() => this.copy(report.searchId)}>
              <Autolink
                style={ButtonStyle.orangeText}
                text={`311# ${report.searchId}`}
              />
            </TouchableOpacity>
          )}
          <View
            style={[
              HorizontalStyle.style,
              {
                paddingTop: 10
              }
            ]}
          >
            <View
              style={{
                flexDirection: "column"
              }}
            >
              <Text>{`${time.fromNow()}`}</Text>
              <Text>{`${time.format("M/D/YY h:mm A")} `}</Text>
            </View>
            <View
              style={{
                flexDirection: "column"
              }}
            >
              <Text style={ButtonStyle.orangeText}>
                {address.cb ? `#CB${address.cb}` : ""}
              </Text>
              <Text>{this.status}</Text>
            </View>
          </View>
        </TypedCard>
        <View
          style={{
            position: "absolute",
            right: 0,
            flexDirection: "row",
            alignItems: "flex-end"
          }}
        >
          {!!report.points ? (
            <TypedTooltip
              popover={
                <Text style={{ color: "white", fontWeight: "bold" }}>
                  {report.points} {report.points != 1 ? "Point" : "Points"}
                  {" added to license."}
                </Text>
              }
              backgroundColor={"black"}
            >
              <TypedAvatar
                rounded
                size={40}
                title={`+${report.points}`}
                overlayContainerStyle={{
                  backgroundColor: "white",
                  borderColor: "black",
                  borderWidth: 2
                }}
                titleStyle={{
                  fontSize: 14,
                  fontWeight: "bold",
                  color: "black"
                }}
              />
            </TypedTooltip>
          ) : null}
          {!!report.fine ? (
            <TypedTooltip
              popover={
                <Text style={{ color: "white", fontWeight: "bold" }}>
                  Fined ${report.fine}
                </Text>
              }
              backgroundColor={"green"}
            >
              <TypedAvatar
                rounded
                size={40}
                title={`$${report.fine}`}
                overlayContainerStyle={{
                  backgroundColor: "white",
                  borderColor: "green",
                  borderWidth: 2
                }}
                titleStyle={{
                  fontSize: 12,
                  fontWeight: "bold",
                  color: "green"
                }}
              />
            </TypedTooltip>
          ) : null}
          {report.media.find(x => x.type && x.type.indexOf("GUILTY") != -1) && (
            <TypedAvatar
              rounded
              size={40}
              onPress={() => {
                Linking.openURL(
                  report.media.find(x => x.type && x.type.indexOf("GUILTY")).url
                );
              }}
              title={`PDF`}
              overlayContainerStyle={{
                backgroundColor: "white",
                borderColor: "red",
                borderWidth: 2
              }}
              titleStyle={{
                fontSize: 14,
                fontWeight: "bold",
                color: "render"
              }}
            />
          )}
        </View>
      </View>
    );
  }
}
