import React, { useContext, useEffect, useState } from 'react';
import { CircularProgress, makeStyles } from '@material-ui/core';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import client from '../../util/client';
import ListItemLink from '../../util/ListItemLink';
import NavbarContext from '../../context/NavbarContext';

const useStyles = makeStyles({
    loading: {
        width: '100vw',
        '& div': {
            margin: '50px auto',
            display: 'block',
        },
    },
});

export default function About() {
    const { setTitle, setAction } = useContext(NavbarContext);
    const classes = useStyles();

    const [about, setAbout] = useState<IAbout>();

    useEffect(() => { setTitle('About'); setAction(<></>); }, []);

    useEffect(() => {
        client.get('/api/v1/settings/about')
            .then((response) => response.data)
            .then((data:IAbout) => {
                setAbout(data);
            });
    }, []);

    if (about === undefined) {
        return (
            <div className={classes.loading}>
                <CircularProgress thickness={5} />
            </div>
        );
    }

    const version = () => {
        if (about.buildType === 'Stable') return `${about.version}`;
        return `${about.version}-${about.revision}`;
    };

    const buildTime = () => new Date(about.buildTime * 1000).toUTCString();

    return (
        <List>
            <ListItem>
                <ListItemText primary="Server" secondary={`${about.name} ${about.buildType}`} />
            </ListItem>
            <ListItem>
                <ListItemText primary="Server version" secondary={version()} />
            </ListItem>
            <ListItem>
                <ListItemText primary="Build time" secondary={buildTime()} />
            </ListItem>
            <ListItemLink href={about.github}>
                <ListItemText primary="Github" secondary={about.github} />
            </ListItemLink>
            <ListItemLink href={about.discord}>
                <ListItemText primary="Discord" secondary={about.discord} />
            </ListItemLink>
        </List>
    );
}
