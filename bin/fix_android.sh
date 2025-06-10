#!/bin/sh

GRADLE_PROPS=android/gradle.properties

grep -q "^android.enableJetifier=" ${GRADLE_PROPS}
if [ "$?" = "0" ]; then
    perl -pi -e "s/^android.enableJetifier=.+?$/android.enableJetifier=true/" ${GRADLE_PROPS}
else
    echo "" >> ${GRADLE_PROPS}
    echo "android.enableJetifier=true" >> ${GRADLE_PROPS}
fi

