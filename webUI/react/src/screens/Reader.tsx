/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import CircularProgress from '@material-ui/core/CircularProgress';
import { makeStyles } from '@material-ui/core/styles';
import React, { useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import HorizontalPager from '../components/reader/pager/HorizontalPager';
import Page from '../components/reader/Page';
import PageNumber from '../components/reader/PageNumber';
import WebtoonPager from '../components/reader/pager/PagedPager';
import VerticalPager from '../components/reader/pager/VerticalPager';
import ReaderNavBar, { defaultReaderSettings } from '../components/navbar/ReaderNavBar';
import NavbarContext from '../context/NavbarContext';
import client from '../util/client';
import useLocalStorage from '../util/useLocalStorage';
import cloneObject from '../util/cloneObject';

const useStyles = (settings: IReaderSettings) => makeStyles({
    root: {
        width: settings.staticNav ? 'calc(100vw - 300px)' : '100vw',
    },

    loading: {
        margin: '50px auto',
    },
});

const getReaderComponent = (readerType: ReaderType) => {
    switch (readerType) {
        case 'ContinuesVertical':
            return VerticalPager;
            break;
        case 'Webtoon':
            return VerticalPager;
            break;
        case 'SingleVertical':
            return WebtoonPager;
            break;
        case 'SingleRTL':
            return WebtoonPager;
            break;
        case 'SingleLTR':
            return WebtoonPager;
            break;
        case 'ContinuesHorizontal':
            return HorizontalPager;
        default:
            return VerticalPager;
            break;
    }
};

const range = (n:number) => Array.from({ length: n }, (value, key) => key);
const initialChapter = () => ({ pageCount: -1, index: -1, chapterCount: 0 });

export default function Reader() {
    const [settings, setSettings] = useLocalStorage<IReaderSettings>('readerSettings', defaultReaderSettings);

    const classes = useStyles(settings)();

    const [serverAddress] = useLocalStorage<String>('serverBaseURL', '');

    const { chapterIndex, mangaId } = useParams<{ chapterIndex: string, mangaId: string }>();
    const [manga, setManga] = useState<IMangaCard | IManga>({ id: +mangaId, title: '', thumbnailUrl: '' });
    const [chapter, setChapter] = useState<IChapter | IPartialChpter>(initialChapter());
    const [curPage, setCurPage] = useState<number>(0);

    const { setOverride, setTitle } = useContext(NavbarContext);
    useEffect(() => {
        // make sure settings has all the keys
        const settingsClone = cloneObject(settings) as any;
        const defualtSettings = defaultReaderSettings();
        let shouldUpdateSettings = false;
        Object.keys(defualtSettings).forEach((key) => {
            const keyOf = key as keyof IReaderSettings;
            if (settings[keyOf] === undefined) {
                settingsClone[keyOf] = defualtSettings[keyOf];
                shouldUpdateSettings = true;
            }
        });
        if (shouldUpdateSettings) { setSettings(settingsClone); }

        // set the custom navbar
        setOverride(
            {
                status: true,
                value: (
                    <ReaderNavBar
                        settings={settings}
                        setSettings={setSettings}
                        manga={manga}
                        chapter={chapter}
                        curPage={curPage}
                    />
                ),
            },
        );

        // clean up for when we leave the reader
        return () => setOverride({ status: false, value: <div /> });
    }, [manga, chapter, settings, curPage, chapterIndex]);

    useEffect(() => {
        setTitle('Reader');
        client.get(`/api/v1/manga/${mangaId}/`)
            .then((response) => response.data)
            .then((data: IManga) => {
                setManga(data);
                setTitle(data.title);
            });
    }, [chapterIndex]);

    useEffect(() => {
        setChapter(initialChapter);
        client.get(`/api/v1/manga/${mangaId}/chapter/${chapterIndex}`)
            .then((response) => response.data)
            .then((data:IChapter) => {
                setChapter(data);
            });
    }, [chapterIndex]);

    if (chapter.pageCount === -1) {
        return (
            <div className={classes.loading}>
                <CircularProgress thickness={5} />
            </div>
        );
    }

    const pages = range(chapter.pageCount).map((index) => ({
        index,
        src: `${serverAddress}/api/v1/manga/${mangaId}/chapter/${chapterIndex}/page/${index}`,
    }));

    const ReaderComponent = getReaderComponent(settings.readerType);

    return (
        <div className={classes.root}>
            <PageNumber
                settings={settings}
                curPage={curPage}
                pageCount={chapter.pageCount}
            />
            <ReaderComponent
                pages={pages}
                pageCount={chapter.pageCount}
                setCurPage={setCurPage}
                curPage={curPage}
                settings={settings}
            />
        </div>
    );
}
