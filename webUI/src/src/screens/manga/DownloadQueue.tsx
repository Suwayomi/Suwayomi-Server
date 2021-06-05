/* eslint-disable @typescript-eslint/no-shadow */
/* eslint-disable react/destructuring-assignment */
/* eslint-disable react/jsx-props-no-spreading */
/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import NavbarContext from 'context/NavbarContext';
import React, { useContext, useEffect, useState } from 'react';
import PlayArrowIcon from '@material-ui/icons/PlayArrow';
import PauseIcon from '@material-ui/icons/Pause';
import IconButton from '@material-ui/core/IconButton';
import client from 'util/client';
import {
    DragDropContext, Draggable, DraggingStyle, Droppable, DropResult, NotDraggingStyle,
} from 'react-beautiful-dnd';
import { useTheme } from '@material-ui/core/styles';
import { Palette } from '@material-ui/core/styles/createPalette';
import List from '@material-ui/core/List';
import DragHandleIcon from '@material-ui/icons/DragHandle';
import ListItem from '@material-ui/core/ListItem';
import { ListItemIcon } from '@material-ui/core';
import ListItemText from '@material-ui/core/ListItemText';

const baseWebsocketUrl = JSON.parse(window.localStorage.getItem('serverBaseURL')!).replace('http', 'ws');

const getItemStyle = (isDragging: boolean,
    draggableStyle: DraggingStyle | NotDraggingStyle | undefined, palette: Palette) => ({
    // styles we need to apply on draggables
    ...draggableStyle,

    ...(isDragging && {
        background: palette.type === 'dark' ? '#424242' : 'rgb(235,235,235)',
    }),
});

const initialQueue = {
    status: 'Stopped',
    queue: [],
} as IQueue;

export default function DownloadQueue() {
    const [, setWsClient] = useState<WebSocket>();
    const [queueState, setQueueState] = useState<IQueue>(initialQueue);
    const { queue, status } = queueState;

    const theme = useTheme();

    const { setTitle, setAction } = useContext(NavbarContext);

    const toggleQueueStatus = () => {
        if (status === 'Stopped') {
            client.get('/api/v1/downloads/start');
        } else {
            client.get('/api/v1/downloads/stop');
        }
    };

    useEffect(() => {
        setTitle('Download Queue');

        setAction(() => {
            if (status === 'Stopped') {
                return (
                    <IconButton onClick={toggleQueueStatus}>
                        <PlayArrowIcon />
                    </IconButton>
                );
            }
            return (
                <IconButton onClick={toggleQueueStatus}>
                    <PauseIcon />
                </IconButton>
            );
        });
    }, [status]);

    useEffect(() => {
        const wsc = new WebSocket(`${baseWebsocketUrl}/api/v1/downloads`);
        wsc.onmessage = (e) => {
            setQueueState(JSON.parse(e.data));
        };

        setWsClient(wsc);
    }, []);

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const onDragEnd = (result: DropResult) => {
    };

    return (
        <>
            <DragDropContext onDragEnd={onDragEnd}>
                <Droppable droppableId="droppable">
                    {(provided) => (
                        <List ref={provided.innerRef}>
                            {queue.map((item, index) => (
                                <Draggable
                                    key={`${item.mangaId}-${item.chapterIndex}`}
                                    draggableId={`${item.mangaId}-${item.chapterIndex}`}
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
                                                primary={
                                                    `${item.chapter.name} | `
                                                    + ` (${item.progress * 100}%)`
                                                    + ` => state: ${item.state}`
                                                }
                                            />
                                            {/* <IconButton
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
                                            </IconButton> */}
                                        </ListItem>
                                    )}
                                </Draggable>
                            ))}
                            {provided.placeholder}
                        </List>
                    )}
                </Droppable>
            </DragDropContext>
        </>
    );
}
