import AsyncStorage from '@react-native-async-storage/async-storage';
import React from 'react';
import { USER_KEY } from './Auth';

interface IAuthContext {
    loading: boolean,
    authorized: boolean,
    userObj: any,
    login: (userObj: object) => Promise<boolean>,
    logout: () => void,
}

const AuthContextDefaults = {
    loading: true,
    authorized: false,
    userObj: null,
    login: () => null,
    logout: () => null,
}

type AuthProviderProps = {
    children: any,
}

const AuthContext = React.createContext<IAuthContext>(AuthContextDefaults);

export default function AuthProvider({ children }: AuthProviderProps) {
    const [authorized, setAuthorized] = React.useState<boolean>(false);
    const [loading, setLoading] = React.useState<boolean>(true);
    const [userObj, setUserObj] = React.useState(null);

    const login = (userObj: any): Promise<boolean> => {
        return new Promise((resolve, reject) => {
            AsyncStorage.setItem(USER_KEY, JSON.stringify(userObj))
                .then(() => {
                    setAuthorized(!!userObj && !!userObj.sessionToken);
                    resolve(true);
                })
                .catch((err) => reject(err));
        })
    }

    const logout = () => {
        return login(null);
    }

    const loadAuthorization = () => {
        AsyncStorage.getItem(USER_KEY)
            .then((res) => {
                let myUserObj = null;
                try {
                    myUserObj = JSON.parse(res);
                } catch { }
                setUserObj(myUserObj);
                setAuthorized(!!myUserObj && !!myUserObj.sessionToken);
                setLoading(false);
            })
    }

    React.useEffect(() => {
        loadAuthorization();
    }, []);

    const values = {
        userObj,
        loading,
        authorized,
    }

    const funcs = {
        login,
        logout,
    }

    return (
        <AuthContext.Provider value={{ ...values, ...funcs }}>
            {children}
        </AuthContext.Provider>
    )
}

export const useAuth = () => React.useContext(AuthContext);
