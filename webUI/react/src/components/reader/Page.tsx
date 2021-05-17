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

const useStyles = (settings: IReaderSettings) => makeStyles({
    loading: {
        margin: '100px auto',
        height: '100vh',
        width: '100vw',
    },
    loadingImage: {
        height: '100vh',
        width: '70vw',
        padding: '50px calc(50% - 20px)',
        backgroundColor: '#525252',
        marginBottom: 10,
    },
    image: {
        display: 'block',
        marginBottom: settings.readerType === 'ContinuesVertical' ? '15px' : 0,
        minWidth: '50vw',
        width: '100%',
        maxWidth: '100%',
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
        if (settings.readerType === 'Webtoon' || settings.readerType === 'ContinuesVertical') {
            window.addEventListener('scroll', handleScroll);

            return () => {
                window.removeEventListener('scroll', handleScroll);
            };
        } return () => {};
    }, [handleScroll]);

    useEffect(() => {
        const img = new Image();
        img.src = src;

        img.onload = () => setImagsrc(src);
    }, [src]);

    if (imageSrc.length === 0) {
        return (
            <div className={`${classes.image} ${classes.loadingImage}`}>
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
        />
    );
}

const Page = React.forwardRef((props: IProps, ref: any) => {
    const {
        src, index, setCurPage, settings,
    } = props;
    const classes = useStyles(settings)();

    return (
        <div ref={ref} style={{ margin: '0 auto' }}>
            <LazyImage
                src={src}
                index={index}
                setCurPage={setCurPage}
                settings={settings}
            />
        </div>
    );
});

export default Page;
