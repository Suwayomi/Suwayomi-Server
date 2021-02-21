/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/* eslint-disable @typescript-eslint/no-shadow */
/* eslint-disable react/destructuring-assignment */
/* eslint-disable react/jsx-props-no-spreading */
import React, { useState, useContext, useEffect } from 'react';
import {
    List,
    ListItem,
    ListItemText,
    ListItemIcon,
    IconButton,
} from '@material-ui/core';
import { DragDropContext, Droppable, Draggable } from 'react-beautiful-dnd';
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
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';
import NavBarTitle from '../../context/NavbarTitle';

const getItemStyle = (isDragging, draggableStyle, palette) => ({
    // styles we need to apply on draggables
    ...draggableStyle,

    ...(isDragging && {
        background: palette.type === 'dark' ? '#424242' : 'rgb(235,235,235)',
    }),
});

export default function Categories() {
    const { setTitle } = useContext(NavBarTitle);
    setTitle('Categories');
    const [categories, setCategories] = useState([]);
    const [categoryToEdit, setCategoryToEdit] = useState(-1); // -1 means new category
    const [dialogOpen, setDialogOpen] = React.useState(false);
    const [dialogValue, setDialogValue] = useState('');
    const theme = useTheme();

    const [updateTriggerHolder, setUpdateTriggerHolder] = useState(0); // just a hack
    const triggerUpdate = () => setUpdateTriggerHolder(updateTriggerHolder + 1); // just a hack

    useEffect(() => {
        if (!dialogOpen) {
            fetch('http://127.0.0.1:4567/api/v1/category/')
                .then((response) => response.json())
                .then((data) => setCategories(data));
        }
    }, [updateTriggerHolder]);

    const categoryReorder = (list, from, to) => {
        const category = list[from];

        const formData = new FormData();
        formData.append('from', from + 1);
        formData.append('to', to + 1);
        fetch(`http://127.0.0.1:4567/api/v1/category/${category.id}/reorder`, {
            method: 'PATCH',
            mode: 'cors',
            body: formData,
        }).finally(() => triggerUpdate());

        // also move it in local state to avoid jarring moving behviour...
        const result = Array.from(list);
        const [removed] = result.splice(from, 1);
        result.splice(to, 0, removed);
        return result;
    };

    const onDragEnd = (result) => {
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

    const handleDialogOpen = () => {
        setDialogOpen(true);
    };

    const resetDialog = () => {
        setDialogOpen(false);
        setDialogValue('');
        setCategoryToEdit(-1);
    };

    const handleDialogCancel = () => {
        resetDialog();
    };

    const handleDialogSubmit = () => {
        resetDialog();

        const formData = new FormData();
        formData.append('name', dialogValue);

        if (categoryToEdit === -1) {
            fetch('http://127.0.0.1:4567/api/v1/category/', {
                method: 'POST',
                mode: 'cors',
                body: formData,
            }).finally(() => triggerUpdate());
        } else {
            const category = categories[categoryToEdit];
            fetch(`http://127.0.0.1:4567/api/v1/category/${category.id}`, {
                method: 'PATCH',
                mode: 'cors',
                body: formData,
            }).finally(() => triggerUpdate());
        }
    };

    const deleteCategory = (index) => {
        const category = categories[index];
        fetch(`http://127.0.0.1:4567/api/v1/category/${category.id}`, {
            method: 'DELETE',
            mode: 'cors',
        }).finally(() => triggerUpdate());
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
                                            ContainerComponent="li"
                                            ContainerProps={{ ref: provided.innerRef }}
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
                                                    setCategoryToEdit(index);
                                                    handleDialogOpen();
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
            <Dialog open={dialogOpen} onClose={handleDialogCancel} aria-labelledby="form-dialog-title">
                <DialogTitle id="form-dialog-title">
                    {categoryToEdit === -1 ? 'New Catalog' : `Rename: ${categories[categoryToEdit].name}`}
                </DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Enter new category name.
                    </DialogContentText>
                    <TextField
                        autoFocus
                        margin="dense"
                        id="name"
                        label="Category Name"
                        type="text"
                        fullWidth
                        value={dialogValue}
                        onChange={(e) => setDialogValue(e.target.value)}
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
