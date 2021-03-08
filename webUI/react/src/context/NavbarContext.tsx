/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React from 'react';

type ContextType = {
    title: string
    setTitle: React.Dispatch<React.SetStateAction<string>>
    action: any
    setAction: React.Dispatch<React.SetStateAction<any>>
};

const NavBarContext = React.createContext<ContextType>({
    title: 'Tachidesk',
    setTitle: ():void => {},
    action: <div />,
    setAction: ():void => {},
});

export default NavBarContext;
