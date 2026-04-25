export const checkForNoNullValuesInArray = (values: unknown[]) => {
    return values.every(val => ![undefined, null].includes(val));
};
