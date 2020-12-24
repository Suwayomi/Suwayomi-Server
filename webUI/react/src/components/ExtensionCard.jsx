import React from "react";
import { makeStyles } from "@material-ui/core/styles";
import Card from "@material-ui/core/Card";
import CardContent from "@material-ui/core/CardContent";
import Button from "@material-ui/core/Button";
import Avatar from "@material-ui/core/Avatar";
import Typography from "@material-ui/core/Typography";

const useStyles = makeStyles((theme) => ({
    root: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: 16
    },
    bullet: {
        display: "inline-block",
        margin: "0 2px",
        transform: "scale(0.8)"
    },
    title: {
        fontSize: 14
    },
    pos: {
        marginBottom: 12
    },
    icon: {
        width: theme.spacing(7),
        height: theme.spacing(7),
        flex: "0 0 auto",
        marginRight: 16
    }
}));

export default function ExtensionCard(props) {
    const classes = useStyles();
    const bull = <span className={classes.bullet}>•</span>;
    const {name, lang, versionName, iconUrl} = props
    const langPress = lang === "all"? "All": lang.toUpperCase()

    return (
        <Card>
            <CardContent className={classes.root}>
                <div style={{display:"flex"}}>
                    <Avatar
                        variant="rounded"
                        className={classes.icon}
                        alt={name}
                        src={iconUrl}
                    />
                    <div style={{display:"flex", flexDirection:"column"}}>
                        <Typography variant="h5" component="h2">
                            {name}
                        </Typography>
                        <Typography variant="caption" display="block" gutterBottom>
                            {langPress} {versionName}
                        </Typography>
                    </div>
                </div>

                <Button variant="outlined" >install</Button>
            </CardContent>
        </Card>
    );
}
