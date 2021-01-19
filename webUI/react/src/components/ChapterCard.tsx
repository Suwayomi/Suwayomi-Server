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

interface IProps{
    chapter: IChapter
}

export default function ChapterCard(props: IProps) {
    const classes = useStyles();
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
                        <div style={{ display: 'flex' }}>
                            <Button variant="outlined" style={{ marginLeft: 20 }} onClick={() => { window.location.href = `/manga/${chapter.mangaId}/chapter/${chapter.id}`; }}>open</Button>
                        </div>
                    </CardContent>
                </Card>
            </li>
        </>
    );
}
