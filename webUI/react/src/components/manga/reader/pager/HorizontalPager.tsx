/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import React from 'react';
import Page from '../Page';

const useStyles = makeStyles({
    reader: {
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'center',
        margin: '0 auto',
        width: '100%',
        height: '100vh',
        overflowX: 'scroll',
    },
});

interface IProps {
    pages: Array<IReaderPage>
    setCurPage: React.Dispatch<React.SetStateAction<number>>
    settings: IReaderSettings
}

export default function HorizontalPager(props: IProps) {
    const { pages, settings, setCurPage } = props;

    const classes = useStyles();

    return (
        <div className={classes.reader}>
            {
                pages.map((page) => (
                    <Page
                        key={page.index}
                        index={page.index}
                        src={page.src}
                        setCurPage={setCurPage}
                        settings={settings}
                    />
                ))
            }
        </div>
    );
}
