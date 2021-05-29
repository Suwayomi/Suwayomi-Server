/* eslint-disable @typescript-eslint/no-shadow */
/* eslint-disable react/destructuring-assignment */
/* eslint-disable react/jsx-props-no-spreading */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import React, { useState, useContext, useEffect } from 'react';
import {
    List,
    ListItem,
    ListItemText,
    ListItemIcon,
    IconButton,
} from '@material-ui/core';
import {
    DragDropContext, Droppable, Draggable, DropResult, DraggingStyle, NotDraggingStyle,
} from 'react-beautiful-dnd';
import DragHandleIcon from '@material-ui/icons/DragHandle';
import EditIcon from '@material-ui/icons/Edit';
import { useTheme } from '@material-ui/core/styles';
import Fab from '@material-ui/core/Fab';
import AddIcon from '@material-ui/icons/Add';
import DeleteIcon from '@material-ui/icons/Delete';
import Button from '@material-ui/core/Button';
import TextField from '@material-ui/core/TextField';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogTitle from '@material-ui/core/DialogTitle';
import Checkbox from '@material-ui/core/Checkbox';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import NavbarContext from 'context/NavbarContext';
import client from 'util/client';
import { Palette } from '@material-ui/core/styles/createPalette';

const getItemStyle = (isDragging: boolean,
    draggableStyle: DraggingStyle | NotDraggingStyle | undefined, palette: Palette) => ({
    // styles we need to apply on draggables
    ...draggableStyle,

    ...(isDragging && {
        background: palette.type === 'dark' ? '#424242' : 'rgb(235,235,235)',
    }),
});

export default function Categories() {
    const { setTitle, setAction } = useContext(NavbarContext);
    useEffect(() => { setTitle('Categories'); setAction(<></>); }, []);

    const [categories, setCategories] = useState<ICategory[]>([]);
    const [categoryToEdit, setCategoryToEdit] = useState<number>(-1); // -1 means new category
    const [dialogOpen, setDialogOpen] = useState<boolean>(false);
    const [dialogName, setDialogName] = useState<string>('');
    const [dialogDefault, setDialogDefault] = useState<boolean>(false);
    const theme = useTheme();

    const [updateTriggerHolder, setUpdateTriggerHolder] = useState<number>(0); // just a hack
    const triggerUpdate = () => setUpdateTriggerHolder(updateTriggerHolder + 1); // just a hack

    useEffect(() => {
        if (!dialogOpen) {
            client.get('/api/v1/category/')
                .then((response) => response.data)
                .then((data) => setCategories(data));
        }
    }, [updateTriggerHolder]);

    const categoryReorder = (list: ICategory[], from: number, to: number) => {
        const category = list[from];

        const formData = new FormData();
        formData.append('from', `${from + 1}`);
        formData.append('to', `${to + 1}`);
        client.post(`/api/v1/category/${category.id}/reorder`, formData)
            .finally(() => triggerUpdate());

        // also move it in local state to avoid jarring moving behviour...
        const result = Array.from(list);
        const [removed] = result.splice(from, 1);
        result.splice(to, 0, removed);
        return result;
    };

    const onDragEnd = (result: DropResult) => {
        // dropped outside the list?
        if (!result.destination) {
            return;
        }

        setCategories(categoryReorder(
            categories,
            result.source.index,
            result.destination.index,
        ));
    };

    const resetDialog = () => {
        setDialogName('');
        setDialogDefault(false);
        setCategoryToEdit(-1);
    };

    const handleDialogOpen = () => {
        resetDialog();
        setDialogOpen(true);
    };

    const handleEditDialogOpen = (index:number) => {
        setDialogName(categories[index].name);
        setDialogDefault(categories[index].default);
        setCategoryToEdit(index);
        setDialogOpen(true);
    };

    const handleDialogCancel = () => {
        setDialogOpen(false);
    };

    const handleDialogSubmit = () => {
        setDialogOpen(false);

        const formData = new FormData();
        formData.append('name', dialogName);
        formData.append('default', dialogDefault.toString());

        if (categoryToEdit === -1) {
            client.post('/api/v1/category/', formData)
                .finally(() => triggerUpdate());
        } else {
            const category = categories[categoryToEdit];
            client.patch(`/api/v1/category/${category.id}`, formData)
                .finally(() => triggerUpdate());
        }
    };

    const deleteCategory = (index:number) => {
        const category = categories[index];
        client.delete(`/api/v1/category/${category.id}`)
            .finally(() => triggerUpdate());
    };

    return (
        <>
            <DragDropContext onDragEnd={onDragEnd}>
                <Droppable droppableId="droppable">
                    {(provided) => (
                        <List ref={provided.innerRef}>
                            {categories.map((item, index) => (
                                <Draggable
                                    key={item.id}
                                    draggableId={item.id.toString()}
                                    index={index}
                                >
                                    {(provided, snapshot) => (
                                        <ListItem
                                            ContainerProps={{ ref: provided.innerRef } as any}
                                            {...provided.draggableProps}
                                            {...provided.dragHandleProps}
                                            style={getItemStyle(
                                                snapshot.isDragging,
                                                provided.draggableProps.style,
                                                theme.palette,
                                            )}
                                            ref={provided.innerRef}
                                        >
                                            <ListItemIcon>
                                                <DragHandleIcon />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={item.name}
                                            />
                                            <IconButton
                                                onClick={() => {
                                                    handleEditDialogOpen(index);
                                                }}
                                            >
                                                <EditIcon />
                                            </IconButton>
                                            <IconButton
                                                onClick={() => {
                                                    deleteCategory(index);
                                                }}
                                            >
                                                <DeleteIcon />
                                            </IconButton>
                                        </ListItem>
                                    )}
                                </Draggable>
                            ))}
                            {provided.placeholder}
                        </List>
                    )}
                </Droppable>
            </DragDropContext>
            <Fab
                color="primary"
                aria-label="add"
                style={{
                    position: 'absolute',
                    bottom: theme.spacing(2),
                    right: theme.spacing(2),
                }}
                onClick={handleDialogOpen}
            >
                <AddIcon />
            </Fab>
            <Dialog open={dialogOpen} onClose={handleDialogCancel}>
                <DialogTitle id="form-dialog-title">
                    {categoryToEdit === -1 ? 'New Catalog' : 'Edit Catalog'}
                </DialogTitle>
                <DialogContent>
                    <TextField
                        autoFocus
                        margin="dense"
                        id="name"
                        label="Category Name"
                        type="text"
                        fullWidth
                        value={dialogName}
                        onChange={(e) => setDialogName(e.target.value)}
                    />
                    <FormControlLabel
                        control={(
                            <Checkbox
                                checked={dialogDefault}
                                onChange={(e) => setDialogDefault(e.target.checked)}
                                color="default"
                            />
                        )}
                        label="Default category when adding new manga to library"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleDialogCancel} color="primary">
                        Cancel
                    </Button>
                    <Button onClick={handleDialogSubmit} color="primary">
                        Submit
                    </Button>
                </DialogActions>
            </Dialog>
        </>

    );
}
