import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';

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

export default function ChapterCard() {
    const name = 'Chapter 1';
    const relaseDate = '16/01/21';
    // const downloaded = false;
    // const downloadedText = downloaded ? 'open' : 'download';
    const classes = useStyles();

    return (
        <>
            <li>
                <Card>
                    <CardContent className={classes.root}>
                        <div style={{ display: 'flex' }}>
                            <div style={{ display: 'flex', flexDirection: 'column' }}>
                                <Typography variant="h5" component="h2">
                                    {name}
                                </Typography>
                                <Typography variant="caption" display="block" gutterBottom>
                                    {relaseDate}
                                </Typography>
                            </div>
                        </div>
                        <div style={{ display: 'flex' }}>
                            <Button variant="outlined" style={{ marginLeft: 20 }} onClick={() => { /* window.location.href = 'sources/popular/'; */ }}>open</Button>
                        </div>
                    </CardContent>
                </Card>
            </li>
        </>
    );
}
