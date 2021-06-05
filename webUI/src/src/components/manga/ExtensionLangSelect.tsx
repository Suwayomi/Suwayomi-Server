/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useState } from 'react';
import { makeStyles, createStyles } from '@material-ui/core/styles';
import Button from '@material-ui/core/Button';
import DialogTitle from '@material-ui/core/DialogTitle';
import DialogContent from '@material-ui/core/DialogContent';
import DialogActions from '@material-ui/core/DialogActions';
import Dialog from '@material-ui/core/Dialog';
import Switch from '@material-ui/core/Switch';
import IconButton from '@material-ui/core/IconButton';
import FilterListIcon from '@material-ui/icons/FilterList';
import { List, ListItemSecondaryAction, ListItemText } from '@material-ui/core';
import ListItem from '@material-ui/core/ListItem';
import { langCodeToName } from 'util/language';
import cloneObject from 'util/cloneObject';

const useStyles = makeStyles(() => createStyles({
    paper: {
        maxHeight: 435,
        width: '80%',
    },
}));

interface IProps {
    shownLangs: string[]
    setShownLangs: (arg0: string[]) => void
    allLangs: string[]
}

export default function ExtensionLangSelect(props: IProps) {
    const { shownLangs, setShownLangs, allLangs } = props;
    // hold a copy and only sate state on parent when OK pressed, improves performance
    const [mShownLangs, setMShownLangs] = useState(shownLangs);
    const classes = useStyles();
    const [open, setOpen] = useState<boolean>(false);

    const handleCancel = () => {
        setOpen(false);
    };

    const handleOk = () => {
        setOpen(false);
        setShownLangs(mShownLangs);
    };

    const handleChange = (event: React.ChangeEvent<HTMLInputElement>, lang: string) => {
        const { checked } = event.target as HTMLInputElement;

        if (checked) {
            setMShownLangs([...mShownLangs, lang]);
        } else {
            const clone = cloneObject(mShownLangs);
            clone.splice(clone.indexOf(lang), 1);
            setMShownLangs(clone);
        }
    };

    return (
        <>
            <IconButton
                onClick={() => setOpen(true)}
                aria-label="display more actions"
                edge="end"
                color="inherit"
            >
                <FilterListIcon />
            </IconButton>
            <Dialog
                classes={classes}
                maxWidth="xs"
                open={open}
            >
                <DialogTitle>Enabled Languages</DialogTitle>
                <DialogContent dividers style={{ padding: 0 }}>
                    <List>
                        {allLangs.map((lang) => (
                            <ListItem key={lang}>
                                <ListItemText primary={langCodeToName(lang)} />

                                <ListItemSecondaryAction>
                                    <Switch
                                        checked={mShownLangs.indexOf(lang) !== -1}
                                        onChange={(e) => handleChange(e, lang)}
                                    />
                                </ListItemSecondaryAction>

                            </ListItem>
                        ))}
                    </List>

                </DialogContent>
                <DialogActions>
                    <Button autoFocus onClick={handleCancel} color="primary">
                        Cancel
                    </Button>
                    <Button onClick={handleOk} color="primary">
                        Ok
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
}
