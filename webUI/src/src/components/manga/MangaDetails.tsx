/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import { makeStyles } from '@material-ui/core';
import IconButton from '@material-ui/core/IconButton';
import { Theme } from '@material-ui/core/styles';
import FavoriteIcon from '@material-ui/icons/Favorite';
import FavoriteBorderIcon from '@material-ui/icons/FavoriteBorder';
import FilterListIcon from '@material-ui/icons/FilterList';
import PublicIcon from '@material-ui/icons/Public';
import React, { useContext, useEffect, useState } from 'react';
import NavbarContext from 'context/NavbarContext';
import client from 'util/client';
import useLocalStorage from 'util/useLocalStorage';
import CategorySelect from './CategorySelect';

const useStyles = (inLibrary: string) => makeStyles((theme: Theme) => ({
    root: {
        width: '100%',
        [theme.breakpoints.up('md')]: {
            position: 'sticky',
            top: '64px',
            left: '0px',
            width: '50vw',
            height: 'calc(100vh - 64px)',
            alignSelf: 'flex-start',
            overflowY: 'auto',
        },
    },
    top: {
        padding: '10px',
        // [theme.breakpoints.up('md')]: {
        //     minWidth: '50%',
        // },
    },
    leftRight: {
        display: 'flex',
    },
    leftSide: {
        '& img': {
            borderRadius: 4,
            maxWidth: '100%',
            minWidth: '100%',
            height: 'auto',
        },
        maxWidth: '50%',
        // [theme.breakpoints.up('md')]: {
        //     minWidth: '100px',
        // },
    },
    rightSide: {
        marginLeft: 15,
        maxWidth: '100%',
        '& span': {
            fontWeight: '400',
        },
        [theme.breakpoints.up('lg')]: {
            fontSize: '1.3em',
        },
    },
    buttons: {
        display: 'flex',
        justifyContent: 'space-around',
        '& button': {
            color: inLibrary === 'In Library' ? '#2196f3' : 'inherit',
        },
        '& span': {
            display: 'block',
            fontSize: '0.85em',
        },
        '& a': {
            textDecoration: 'none',
            color: '#858585',
            '& button': {
                color: 'inherit',
            },
        },
    },
    bottom: {
        paddingLeft: '10px',
        paddingRight: '10px',
        [theme.breakpoints.up('md')]: {
            fontSize: '1.2em',
            // maxWidth: '50%',
        },
        [theme.breakpoints.up('lg')]: {
            fontSize: '1.3em',
        },
    },
    description: {
        '& h4': {
            marginTop: '1em',
            marginBottom: 0,
        },
        '& p': {
            textAlign: 'justify',
            textJustify: 'inter-word',
        },
    },
    genre: {
        display: 'flex',
        flexWrap: 'wrap',
        '& h5': {
            border: '2px solid #2196f3',
            borderRadius: '1.13em',
            marginRight: '1em',
            marginTop: 0,
            marginBottom: '10px',
            padding: '0.3em',
            color: '#2196f3',
        },
    },
}));

interface IProps{
    manga: IManga
}

function getSourceName(source: ISource) {
    if (source.name !== null) {
        return `${source.name} (${source.lang.toLocaleUpperCase()})`;
    }
    return source.id;
}

function getValueOrUnknown(val: string) {
    return val || 'UNKNOWN';
}

export default function MangaDetails(props: IProps) {
    const { setAction } = useContext(NavbarContext);

    const { manga } = props;
    if (manga.genre == null) {
        manga.genre = '';
    }
    const [inLibrary, setInLibrary] = useState<string>(
        manga.inLibrary ? 'In Library' : 'Add To Library',
    );

    const [categoryDialogOpen, setCategoryDialogOpen] = useState<boolean>(false);

    useEffect(() => {
        if (inLibrary === 'In Library') {
            setAction(
                <>
                    <IconButton
                        onClick={() => setCategoryDialogOpen(true)}
                        aria-label="display more actions"
                        edge="end"
                        color="inherit"
                    >
                        <FilterListIcon />
                    </IconButton>
                    <CategorySelect
                        open={categoryDialogOpen}
                        setOpen={setCategoryDialogOpen}
                        mangaId={manga.id}
                    />
                </>,

            );
        } else { setAction(<></>); }
    }, [inLibrary, categoryDialogOpen]);

    const [serverAddress] = useLocalStorage<String>('serverBaseURL', '');

    const classes = useStyles(inLibrary)();

    function addToLibrary() {
        // setInLibrary('adding');
        client.get(`/api/v1/manga/${manga.id}/library/`).then(() => {
            setInLibrary('In Library');
        });
    }

    function removeFromLibrary() {
        // setInLibrary('removing');
        client.delete(`/api/v1/manga/${manga.id}/library/`).then(() => {
            setInLibrary('Add To Library');
        });
    }

    function handleButtonClick() {
        if (inLibrary === 'Add To Library') {
            addToLibrary();
        } else {
            removeFromLibrary();
        }
    }

    return (
        <div className={classes.root}>
            <div className={classes.top}>
                <div className={classes.leftRight}>
                    <div className={classes.leftSide}>
                        <img src={`${serverAddress}${manga.thumbnailUrl}`} alt="Manga Thumbnail" />
                    </div>
                    <div className={classes.rightSide}>
                        <h1>
                            {manga.title}
                        </h1>
                        <h3>
                            Author:
                            {' '}
                            <span>{getValueOrUnknown(manga.author)}</span>
                        </h3>
                        <h3>
                            Artist:
                            {' '}
                            <span>{getValueOrUnknown(manga.artist)}</span>
                        </h3>
                        <h3>
                            Status:
                            {' '}
                            {manga.status}
                        </h3>
                        <h3>
                            Source:
                            {' '}
                            {getSourceName(manga.source)}
                        </h3>
                    </div>
                </div>
                <div className={classes.buttons}>
                    <div>
                        <IconButton onClick={() => handleButtonClick()}>
                            {inLibrary === 'In Library' && <FavoriteIcon />}
                            {inLibrary !== 'In Library' && <FavoriteBorderIcon />}
                            <span>{inLibrary}</span>
                        </IconButton>
                    </div>
                    { /* eslint-disable-next-line react/jsx-no-target-blank */ }
                    <a href={manga.url} target="_blank">
                        <IconButton>
                            <PublicIcon />
                            <span>Open Site</span>
                        </IconButton>
                    </a>
                </div>
            </div>
            <div className={classes.bottom}>
                <div className={classes.description}>
                    <h4>About</h4>
                    <p>{manga.description}</p>
                </div>
                <div className={classes.genre}>
                    {manga.genre.split(', ').map((g) => <h5 key={g}>{g}</h5>)}
                </div>
            </div>
        </div>
    );
}
