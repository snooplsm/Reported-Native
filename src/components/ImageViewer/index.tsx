import React from "react";
import { View } from "react-native";
import { Icon } from "react-native-elements";
import ImageZoomViewer from "react-native-image-zoom-viewer";

import styles from "./styles";

type ImageViewerProps = {
  imagesURLs: Array<{ url: string; [key: string]: unknown }>;
  onCloseModal: () => void;
  onRemoveImage: (index?: number) => void;
};

const ImageViewer = ({imagesURLs, onCloseModal, onRemoveImage}: ImageViewerProps) => {
  return (
    <ImageZoomViewer
      style={styles.viewerComponent}
      onClick={() => onCloseModal()}
      footerContainerStyle={styles.footerContainer}
      renderFooter={index => {
        return (
          <View
            style={styles.footerView}
          >
            <Icon
              iconStyle={{
                padding: 14
              }}
              size={26}
              color="white"
              type="material"
              name="remove-circle-outline"
              onPress={() => onRemoveImage(index)}
            />
          </View>
        );
      }}
      imageUrls={imagesURLs}
    />
  );
};

export default ImageViewer
