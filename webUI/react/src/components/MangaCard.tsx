import React from 'react';
import { makeStyles } from '@material-ui/core/styles';
import Card from '@material-ui/core/Card';
import CardActionArea from '@material-ui/core/CardActionArea';
import CardMedia from '@material-ui/core/CardMedia';
import Typography from '@material-ui/core/Typography';
import { Link } from 'react-router-dom';
import { Grid } from '@material-ui/core';

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
    manga: IManga
}
const MangaCard = React.forwardRef((props: IProps, ref) => {
    const {
        manga: {
            id, title, thumbnailUrl,
        },
    } = props;
    const classes = useStyles();

    return (
        <Grid item xs={6} sm={4} md={3} lg={2}>
            <Link to={`/manga/${id}/`}>
                <Card className={classes.root} ref={ref}>
                    <CardActionArea>
                        <div className={classes.wrapper}>
                            <CardMedia
                                className={classes.image}
                                component="img"
                                alt={title}
                                image={thumbnailUrl}
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

export default MangaCard;
