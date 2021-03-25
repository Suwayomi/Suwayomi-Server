/* eslint-disable @typescript-eslint/no-unused-vars */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import { Link, useHistory } from 'react-router-dom';

const useStyles = makeStyles((theme) => ({
    root: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: 16,
    },
    bullet: {
        display: 'inline-block',
        margin: '0 2px',
        transform: 'scale(0.8)',
    },
    title: {
        fontSize: 14,
    },
    pos: {
        marginBottom: 12,
    },
    icon: {
        width: theme.spacing(7),
        height: theme.spacing(7),
        flex: '0 0 auto',
        marginRight: 16,
    },
}));

interface IProps{
    chapter: IChapter
}

export default function ChapterCard(props: IProps) {
    const classes = useStyles();
    const history = useHistory();
    const { chapter } = props;

    const dateStr = chapter.date_upload && new Date(chapter.date_upload).toISOString().slice(0, 10);

    return (
        <>
            <li>
                <Card>
                    <CardContent className={classes.root}>
                        <div style={{ display: 'flex' }}>
                            <div style={{ display: 'flex', flexDirection: 'column' }}>
                                <Typography variant="h5" component="h2">
                                    {chapter.name}
                                    {chapter.chapter_number > 0 && ` : ${chapter.chapter_number}`}
                                </Typography>
                                <Typography variant="caption" display="block" gutterBottom>
                                    {chapter.scanlator}
                                    {chapter.scanlator && ' '}
                                    {dateStr}
                                </Typography>
                            </div>
                        </div>
                        <Link
                            to={`/manga/${chapter.mangaId}/chapter/${chapter.chapterIndex}`}
                            style={{ textDecoration: 'none' }}
                        >
                            <Button
                                variant="outlined"
                                style={{ marginLeft: 20 }}
                            >
                                open

                            </Button>
                        </Link>

                    </CardContent>
                </Card>
            </li>
        </>
    );
}
