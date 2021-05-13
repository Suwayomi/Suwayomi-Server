/* eslint-disable react/no-unused-prop-types */
/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import CircularProgress from '@material-ui/core/CircularProgress';
import { makeStyles } from '@material-ui/core/styles';
import React, { useEffect, useRef, useState } from 'react';
import LazyLoad from 'react-lazyload';
import { IReaderSettings } from './ReaderNavBar';

const useStyles = (settings: IReaderSettings) => makeStyles({
    loading: {
        margin: '100px auto',
        height: '100vh',
    },
    loadingImage: {
        padding: settings.staticNav ? 'calc(50vh - 40px) calc(50vw - 340px)' : 'calc(50vh - 40px) calc(50vw - 40px)',
        height: '100vh',
        width: '200px',
        backgroundColor: '#525252',
        marginBottom: 10,
    },
    image: {
        display: 'block',
        marginBottom: settings.continuesPageGap ? '15px' : 0,
    },
});

interface IProps {
    src: string
    index: number
    setCurPage: React.Dispatch<React.SetStateAction<number>>
    settings: IReaderSettings
}

function LazyImage(props: IProps) {
    const {
        src, index, setCurPage, settings,
    } = props;

    const classes = useStyles(settings)();
    const [imageSrc, setImagsrc] = useState<string>('');
    const ref = useRef<HTMLImageElement>(null);

    const handleScroll = () => {
        if (ref.current) {
            const rect = ref.current.getBoundingClientRect();
            if (rect.y < 0 && rect.y + rect.height > 0) {
                setCurPage(index);
            }
        }
    };

    useEffect(() => {
        window.addEventListener('scroll', handleScroll);

        return () => {
            window.removeEventListener('scroll', handleScroll);
        };
    }, [handleScroll]);

    useEffect(() => {
        const img = new Image();
        img.src = src;

        img.onload = () => setImagsrc(src);
    }, [src]);

    if (imageSrc.length === 0) {
        return (
            <div className={classes.loadingImage}>
                <CircularProgress thickness={5} />
            </div>
        );
    }

    return (
        <img
            className={classes.image}
            ref={ref}
            src={imageSrc}
            alt={`Page #${index}`}
            style={{ width: '100%', maxWidth: '95vw' }}
        />
    );
}

export default function Page(props: IProps) {
    const {
        src, index, setCurPage, settings,
    } = props;
    const classes = useStyles(settings)();

    return (
        <div style={{ margin: '0 auto' }}>
            <LazyLoad
                offset={window.innerHeight}
                placeholder={(
                    <div className={classes.loading}>
                        <CircularProgress thickness={5} />
                    </div>
                )}
                once
            >
                <LazyImage
                    src={src}
                    index={index}
                    setCurPage={setCurPage}
                    settings={settings}
                />
            </LazyLoad>
        </div>
    );
}
