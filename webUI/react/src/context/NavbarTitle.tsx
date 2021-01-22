import React from 'react';

type ContextType = {
    title: string
    setTitle: React.Dispatch<React.SetStateAction<string>>
};

const NavBarTitle = React.createContext<ContextType>({
    title: 'Tachidesk',
    setTitle: ():void => {},
});

export default NavBarTitle;
