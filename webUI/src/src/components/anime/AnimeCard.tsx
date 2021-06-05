/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Card from '@material-ui/core/Card';
import CardActionArea from '@material-ui/core/CardActionArea';
import CardMedia from '@material-ui/core/CardMedia';
import Typography from '@material-ui/core/Typography';
import { Link } from 'react-router-dom';
import { Grid } from '@material-ui/core';
import useLocalStorage from 'util/useLocalStorage';

const useStyles = makeStyles({
    root: {
        height: '100%',
        width: '100%',
        display: 'flex',
    },
    wrapper: {
        position: 'relative',
        height: '100%',
    },
    gradient: {
        position: 'absolute',
        top: 0,
        width: '100%',
        height: '100%',
        background: 'linear-gradient(to bottom, transparent, #000000)',
        opacity: 0.5,
    },
    title: {
        position: 'absolute',
        bottom: 0,
        padding: '0.5em',
        color: 'white',
    },
    image: {
        height: '100%',
        width: '100%',
    },
});

interface IProps {
    manga: IMangaCard
}
const AnimeCard = React.forwardRef((props: IProps, ref) => {
    const {
        manga: {
            id, title, thumbnailUrl,
        },
    } = props;
    const classes = useStyles();
    const [serverAddress] = useLocalStorage<String>('serverBaseURL', '');

    return (
        <Grid item xs={6} sm={4} md={3} lg={2}>
            <Link to={`/anime/${id}/`}>
                <Card className={classes.root} ref={ref}>
                    <CardActionArea>
                        <div className={classes.wrapper}>
                            <CardMedia
                                className={classes.image}
                                component="img"
                                alt={title}
                                image={serverAddress + thumbnailUrl}
                                title={title}
                            />
                            <div className={classes.gradient} />
                            <Typography className={classes.title} variant="h5" component="h2">{title}</Typography>
                        </div>
                    </CardActionArea>
                </Card>
            </Link>
        </Grid>
    );
});

export default AnimeCard;
