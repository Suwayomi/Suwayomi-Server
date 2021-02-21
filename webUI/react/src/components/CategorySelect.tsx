/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useEffect, useState } from 'react';
import { makeStyles, createStyles } from '@material-ui/core/styles';
import Button from '@material-ui/core/Button';
import DialogTitle from '@material-ui/core/DialogTitle';
import DialogContent from '@material-ui/core/DialogContent';
import DialogActions from '@material-ui/core/DialogActions';
import Dialog from '@material-ui/core/Dialog';
import Checkbox from '@material-ui/core/Checkbox';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import FormGroup from '@material-ui/core/FormGroup';

const useStyles = makeStyles(() => createStyles({
    paper: {
        maxHeight: 435,
        width: '80%',
    },
}));

interface IProps {
    open: boolean
    setOpen: (value: boolean) => void
    mangaId: number
}

interface ICategoryInfo {
    category: ICategory
    selected: boolean
}

export default function CategorySelect(props: IProps) {
    const classes = useStyles();
    const { open, setOpen, mangaId } = props;
    const [categoryInfos, setCategoryInfos] = useState<ICategoryInfo[]>([]);

    const [updateTriggerHolder, setUpdateTriggerHolder] = useState(0); // just a hack
    const triggerUpdate = () => setUpdateTriggerHolder(updateTriggerHolder + 1); // just a hack

    useEffect(() => {
        let tmpCategoryInfos: ICategoryInfo[] = [];
        fetch('http://127.0.0.1:4567/api/v1/category/')
            .then((response) => response.json())
            .then((data: ICategory[]) => {
                tmpCategoryInfos = data.map((category) => ({ category, selected: false }));
            })
            .then(() => {
                fetch(`http://127.0.0.1:4567/api/v1/manga/${mangaId}/category/`)
                    .then((response) => response.json())
                    .then((data: ICategory[]) => {
                        data.forEach((category) => {
                            tmpCategoryInfos[category.order - 1].selected = true;
                        });
                        setCategoryInfos(tmpCategoryInfos);
                    });
            });
    }, [updateTriggerHolder]);

    const handleCancel = () => {
        setOpen(false);
    };

    const handleOk = () => {
        setOpen(false);
    };

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const handleChange = (event: React.ChangeEvent<HTMLInputElement>, categoryId: number) => {
        const { checked } = event.target as HTMLInputElement;
        fetch(`http://127.0.0.1:4567/api/v1/manga/${mangaId}/category/${categoryId}`, {
            method: checked ? 'GET' : 'DELETE', mode: 'cors',
        })
            .then(() => triggerUpdate());
    };

    return (
        <Dialog
            classes={classes}
            maxWidth="xs"
            open={open}
        >
            <DialogTitle>Set categories</DialogTitle>
            <DialogContent dividers>
                <FormGroup>
                    {categoryInfos.map((categoryInfo) => (
                        <FormControlLabel
                            control={(
                                <Checkbox
                                    checked={categoryInfo.selected}
                                    onChange={(e) => handleChange(e, categoryInfo.category.id)}
                                    name="checkedB"
                                    color="default"
                                />
                            )}
                            label={categoryInfo.category.name}
                        />
                    ))}
                </FormGroup>

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
    );
}
