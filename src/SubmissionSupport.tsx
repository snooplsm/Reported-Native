import React from "react";
import { StyleSheet, View } from "react-native";
import moment from "moment";

import ComplaintView from "./ComplaintView";
import ImageViewer from "./components/ImageViewer";
import { colors } from "./Styles";
import { SUBMIT_BUTTON_HEIGHT, SUBMIT_BUTTON_PADDING_VERTICAL } from "./common/dimen";

type ComplaintModalProps = {
  visible: boolean;
  onComplaintsChanged: (complaints: any[]) => void;
};

export function SubmissionComplaintModal({
  visible,
  onComplaintsChanged
}: ComplaintModalProps) {
  if (!visible) return null;
  return (
    <View style={styles.complaintModal}>
      <ComplaintView onComplaintsChanged={onComplaintsChanged} />
    </View>
  );
}

type ImageModalProps = {
  visible: boolean;
  images: any[];
  onClose: () => void;
  onRemove: (index: number) => void;
};

export function SubmissionImageModal({
  visible,
  images,
  onClose,
  onRemove
}: ImageModalProps) {
  if (!visible) return null;
  return (
    <ImageViewer
      imagesURLs={images}
      onCloseModal={onClose}
      onRemoveImage={onRemove}
    />
  );
}

export function getTimeofreport(timeofreport: any) {
  if (!timeofreport) return undefined;
  const value = moment(timeofreport);
  return value.isValid() ? `${value.fromNow()} @ ${value.format("M/D h:mm a")}` : "";
}

export function getAddPhotoText(media: any[]) {
  return media?.length ? "Add Another Photo" : "Add Photo";
}

export const styles = StyleSheet.create({
  addPhoto: {
    height: 55
  },
  addPhotoContainer: {
    padding: 10
  },
  submitButtonStyle: {
    backgroundColor: colors.orange
  },
  button: {
    width: "30%",
    height: SUBMIT_BUTTON_HEIGHT
  },
  container: {
    width: "100%",
    height: "100%",
    justifyContent: "space-between"
  },
  imageViewer: {
    backgroundColor: "yellow",
    width: 200,
    height: 200
  },
  submitButtonWrapper: {
    paddingHorizontal: 10,
    paddingVertical: SUBMIT_BUTTON_PADDING_VERTICAL
  },
  inputWrapper: {
    marginLeft: -8,
    marginRight: -8
  },
  complaintModal: {
    position: "absolute",
    bottom: 0,
    top: 0,
    left: 0,
    right: 0,
    zIndex: 9999,
    backgroundColor: "white"
  }
});
