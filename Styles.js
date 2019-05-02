import { StyleSheet } from "react-native";

export const ErrorStyle = StyleSheet.create({
  style: {
    color: "red",
  },
});

export const ButtonContainerStyle = StyleSheet.create({
  style: {
    position: "absolute",
    flex: 1,
    width: "100%",
    justifyContent: "center",
    alignItems: "center",
    bottom: "45%",
    padding: 0,
  },
  bottom: {
    flex: 1,
    position: "absolute",
    width: "100%",
    bottom: 0,
    padding: 0,
  },
});

export const colors = {
  orange: "#ec682c",
  white: "#eeeeee",
};

export const ButtonStyle = StyleSheet.create({
  primary: {
    backgroundColor: colors.orange,
  },
  outline: {
    borderColor: colors.orange,
    backgroundColor: colors.white,
  },
  whiteText: {
    color: colors.white,
  },
  orangeText: {
    color: colors.orange,
  },
});
