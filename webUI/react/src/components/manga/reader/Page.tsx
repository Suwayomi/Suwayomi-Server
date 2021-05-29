/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core/styles';
import { CSSProperties } from '@material-ui/core/styles/withStyles';
import React, { useEffect, useRef } from 'react';
import SpinnerImage from 'components/SpinnerImage';

function imageStyle(settings: IReaderSettings): CSSProperties {
    if (settings.readerType === 'DoubleLTR'
    || settings.readerType === 'DoubleRTL'
    || settings.readerType === 'ContinuesHorizontalLTR'
    || settings.readerType === 'ContinuesHorizontalRTL') {
        return {
            display: 'block',
            marginLeft: '7px',
            marginRight: '7px',
            width: 'auto',
            minHeight: '99vh',
            height: 'auto',
            maxHeight: '99vh',
            objectFit: 'contain',
            pointerEvents: 'none',
        };
    }

    return {
        display: 'block',
        marginBottom: settings.readerType === 'ContinuesVertical' ? '15px' : 0,
        minWidth: '50vw',
        width: '100%',
        maxWidth: '100%',
    };
}

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
    image: imageStyle(settings),
});

interface IProps {
    src: string
    index: number
    onImageLoad: () => void
    setCurPage: React.Dispatch<React.SetStateAction<number>>
    settings: IReaderSettings
}

const Page = React.forwardRef((props: IProps, ref: any) => {
    const {
        src, index, onImageLoad, setCurPage, settings,
    } = props;

    const classes = useStyles(settings)();
    const imgRef = useRef<HTMLImageElement>(null);

    const handleVerticalScroll = () => {
        if (imgRef.current) {
            const rect = imgRef.current.getBoundingClientRect();
            if (rect.y < 0 && rect.y + rect.height > 0) {
                setCurPage(index);
            }
        }
    };

    const handleHorizontalScroll = () => {
        if (imgRef.current) {
            const rect = imgRef.current.getBoundingClientRect();
            if (rect.left <= window.innerWidth / 2 && rect.right > window.innerWidth / 2) {
                setCurPage(index);
            }
        }
    };

    useEffect(() => {
        switch (settings.readerType) {
            case 'Webtoon':
            case 'ContinuesVertical':
                window.addEventListener('scroll', handleVerticalScroll);
                return () => window.removeEventListener('scroll', handleVerticalScroll);
            case 'ContinuesHorizontalLTR':
            case 'ContinuesHorizontalRTL':
                window.addEventListener('scroll', handleHorizontalScroll);
                return () => window.removeEventListener('scroll', handleHorizontalScroll);
            default:
                return () => {};
        }
    }, [handleVerticalScroll]);

    return (
        <div ref={ref} style={{ margin: '0 auto' }}>
            <SpinnerImage
                src={src}
                onImageLoad={onImageLoad}
                alt={`Page #${index}`}
                imgRef={imgRef}
                spinnerClassName={`${classes.image} ${classes.loadingImage}`}
                imgClassName={classes.image}
            />
        </div>
    );
});

export default Page;
