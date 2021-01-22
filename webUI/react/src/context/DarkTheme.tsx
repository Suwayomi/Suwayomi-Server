import React from 'react';

type ContextType = {
    darkTheme: boolean
    setDarkTheme: React.Dispatch<React.SetStateAction<boolean>>
};

const DarkTheme = React.createContext<ContextType>({
    darkTheme: true,
    setDarkTheme: ():void => {},
});

export default DarkTheme;
