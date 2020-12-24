import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Drawer from '@material-ui/core/Drawer';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import InboxIcon from '@material-ui/icons/MoveToInbox';
import { Link } from 'react-router-dom';

const useStyles = makeStyles({
    list: {
        width: 250,
    },
});

interface IProps {
    drawerOpen: boolean
    setDrawerOpen(state: boolean): void
}

export default function TemporaryDrawer({ drawerOpen, setDrawerOpen }: IProps) {
    const classes = useStyles();

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const sideList = (side: 'left') => (
        <div
            className={classes.list}
            role="presentation"
            onClick={() => setDrawerOpen(false)}
            onKeyDown={() => setDrawerOpen(false)}
        >
            <List>
                <Link to="/extensions" style={{ color: 'inherit', textDecoration: 'none' }}>
                    <ListItem button key="Extensions">
                        <ListItemIcon>
                            <InboxIcon />
                        </ListItemIcon>
                        <ListItemText primary="Extensions" />
                    </ListItem>
                </Link>
            </List>
        </div>
    );

    return (
        <div>
            <Drawer
                BackdropProps={{ invisible: true }}
                open={drawerOpen}
                anchor="left"
                onClose={() => setDrawerOpen(false)}
            >
                {sideList('left')}
            </Drawer>
        </div>
    );
}
