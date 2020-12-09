
// Validates an email using regex. Returns true if valid.
// @param [string] email
// @return [boolean]
export const validateEmail = (email) => {
    const re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
}

// Convert a given second to milliseconds for parsing.
// Ex seconds(5) => 5000
// @param [number] second
// @return [number]
export const seconds = (second) => (second * 1000)
