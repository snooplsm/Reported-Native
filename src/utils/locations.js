export const getLocationDataFromExif = (exif) => {
  let {
    GPSAltitude: altitude,
    GPSLatitude: lat,
    GPSLatitudeRef: latRef,
    GPSLongitude: lng,
    GPSLongitudeRef: lngRef
  } = exif;
  // In some cases the latitude is positive for south and sometimes - it's negative. Here we handle it
  lat = (latRef === 'S' && lat > 0) ? -lat : lat;
  lng = (lngRef === 'W' && lng > 0) ? -lng : lng;

  return {
    lat, lng, altitude
  }
}

export const checkAddressNotBelongsToNY = (place) => {
  const address = place.address_components;
  const administrative_area_level_1 = findInLocation(address, "administrative_area_level_1");
  return !!(!administrative_area_level_1 || administrative_area_level_1.toUpperCase() !== 'NY');
}

export const findInLocation = (address, key) => {
  const res = address
    .filter(x => x.types.includes(key))
    .map(x => x.short_name)
    .shift()
  return res
};

