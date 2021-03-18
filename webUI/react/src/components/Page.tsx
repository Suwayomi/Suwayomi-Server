/* eslint-disable @typescript-eslint/no-unused-vars */
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import CircularProgress from '@material-ui/core/CircularProgress';
import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useState } from 'react';
import LazyLoad from 'react-lazyload';

const useStyles = makeStyles({
    loading: {
        margin: '100px auto',
        height: '100vh',
    },
    loadingImage: {
        padding: 'calc(50vh - 40px) calc(50vw - 40px)',
        height: '100vh',
        width: '100vw',
        backgroundColor: '#525252',
        marginBottom: 10,
    },
});

interface IProps {
    src: string
    index: number
}

function LazyImage(props: IProps) {
    const classes = useStyles();
    const { src, index } = props;
    const [imageSrc, setImagsrc] = useState<string>('');

    useEffect(() => {
        const img = new Image();
        img.src = src;

        img.onload = () => setImagsrc(src);
    }, []);

    if (imageSrc.length === 0) {
        return (
            <div className={classes.loadingImage}>
                <CircularProgress thickness={5} />
            </div>
        );
    }

    return (
        <img src={imageSrc} alt={`Page #${index}`} style={{ maxWidth: '100%' }} />
    );
}

export default function Page(props: IProps) {
    const { src, index } = props;
    const classes = useStyles();

    return (
        <div style={{ margin: '0 auto' }}>
            <LazyLoad
                offset={window.innerHeight}
                once
                placeholder={(
                    <div className={classes.loading}>
                        <CircularProgress thickness={5} />
                    </div>
                )}
            >
                <LazyImage src={src} index={index} />
            </LazyLoad>
        </div>
    );
}
