build=$(jq '.expo.ios.buildNumber | tonumber' app.json)
((++build))
echo "Building new build: $build"
jq --arg b  $build '.expo.ios.buildNumber = $b' app.json > tmp.json
rm app.json
mv tmp.json app.json

expo build:ios --release-channel prod
# EXPO_IOS_DIST_P12_PASSWORD=reported ./node_modules/.bin/expo build:ios --dist-p12-path cert.p12 --push-p8-path apns.p8 --provisioning-profile-path prov.mobileprovision --push-id 3DF465649Y --release-channel prod