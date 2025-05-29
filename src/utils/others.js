export const checkForNoNullValuesInArray = (values) => {
    return values.every(val => ![undefined, null].includes(val))
}
