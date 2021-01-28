import {StyleSheet} from "react-native";

export default styles = StyleSheet.create({
    viewerComponent: {
        position: "absolute",
        bottom: 0,
        top: 0,
        left: 0,
        right: 0
    },
    footerContainer: {
        bottom: 0,
        left: 0,
        right: 0,
        position: "absolute",
        zIndex: 9999
    },
    footerView: {
        flex: 1,
        flexDirection: "row",
        flexWrap: "nowrap",
        justifyContent: "flex-end"
    }
});